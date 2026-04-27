package com.bidking.service;

import com.bidking.dto.EvaluationResult;
import com.bidking.dto.GameRoom;
import com.bidking.entity.GameConfig;
import com.bidking.enums.BidFeedback;
import com.bidking.enums.GameState;
import com.bidking.enums.ItemType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * 房间生命周期 + 状态机驱动
 * 唯一有权修改 GameState 的 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomManager {

    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;
    private final GameConfigService gameConfigService;
    private final RngManager rngManager;
    private final TimerService timerService;
    private final SimpMessagingTemplate messaging;
    private final BidEvaluator bidEvaluator;
    private final RevealEngine revealEngine;
    private final InfoBroker infoBroker;
    private final SkillEngine skillEngine;

    private static final String BIDS_KEY = "game:bids:%s:%d";
    private static final String STATE_KEY = "game:player:state:%s:%s";
    // 记录游戏开始时间，用于结算
    private final Map<String, LocalDateTime> startTimes = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String ROOM_KEY = "game:room:";
    private static final String PLAYER_ROOM_KEY = "game:player:room:";
    private static final long ROOM_TTL_HOURS = 2;

    private static final java.util.Set<String> ALL_CHARACTERS =
            java.util.Set.of("LAOTOU", "AISHA", "ETHAN", "OILMAN", "SOHAI", "ISABELLA");

    // ── 房间 CRUD ──────────────────────────────────────────────

    /** 创建房间，返回 roomId */
    @SneakyThrows
    public String createRoom(Long hostId) {
        String oldRoomId = redis.opsForValue().get(PLAYER_ROOM_KEY + hostId);
        if (oldRoomId != null) {
            String oldJson = redis.opsForValue().get(ROOM_KEY + oldRoomId);
            if (oldJson != null) {
                GameRoom oldRoom = objectMapper.readValue(oldJson, GameRoom.class);
                if (oldRoom.getState() == GameState.WAITING) {
                    if (oldRoom.getHostId().equals(hostId) && oldRoom.getPlayerIds().size() == 1) {
                        // 自己创建的空房间，直接删除
                        redis.delete(ROOM_KEY + oldRoomId);
                        log.info("[Room] 清理旧空房间 roomId={}", oldRoomId);
                    } else if (!oldRoom.getHostId().equals(hostId)) {
                        // 在别人的房间里，静默退出
                        oldRoom.getPlayerIds().remove(hostId);
                        oldRoom.getReadyPlayerIds().remove(hostId);
                        saveRoom(oldRoom);
                        log.info("[Room] 玩家={} 退出旧房间={}", hostId, oldRoomId);
                    }
                }
            }
        }

        String roomId = UUID.randomUUID().toString();
        GameConfig config = gameConfigService.get();

        GameRoom room = new GameRoom();
        room.setRoomId(roomId);
        room.setHostId(hostId);
        room.setConfig(config);
        room.setPlayerIds(new ArrayList<>(List.of(hostId)));

        saveRoom(room);
        bindPlayerRoom(hostId, roomId);

        log.info("[Room] 创建房间 roomId={} hostId={} playerCount配置={}", roomId, hostId, config.getPlayerCount());
        return roomId;
    }

    /** 玩家加入房间（一人一房间：已在其他 WAITING 房间时自动退出旧房） */
    @SneakyThrows
    public void joinRoom(String roomId, Long playerId) {
        RLock lock = redisson.getLock("lock:room:" + roomId);
        lock.lock(5, TimeUnit.SECONDS);
        try {
            GameRoom room = getRoom(roomId);
            if (room.getState() != GameState.WAITING) {
                throw new IllegalStateException("房间已开始，无法加入");
            }
            if (room.getPlayerIds().contains(playerId)) {
                log.warn("[Room] 玩家={} 已在房间={} 中，忽略重复加入", playerId, roomId);
                return;
            }
            if (room.getPlayerIds().size() >= room.getConfig().getPlayerCount()) {
                throw new IllegalStateException("房间已满");
            }

            // 一人一房间：如果玩家已在其他 WAITING 房间，自动退出旧房
            String oldRoomId = redis.opsForValue().get(PLAYER_ROOM_KEY + playerId);
            if (oldRoomId != null && !oldRoomId.equals(roomId)) {
                String oldJson = redis.opsForValue().get(ROOM_KEY + oldRoomId);
                if (oldJson != null) {
                    GameRoom oldRoom = objectMapper.readValue(oldJson, GameRoom.class);
                    if (oldRoom.getState() == GameState.WAITING) {
                        oldRoom.getPlayerIds().remove(playerId);
                        oldRoom.getReadyPlayerIds().remove(playerId);
                        oldRoom.getPlayerCharacters().remove(String.valueOf(playerId));
                        saveRoom(oldRoom);
                        log.info("[Room] 玩家={} 退出旧房间={}", playerId, oldRoomId);
                    }
                }
            }

            room.getPlayerIds().add(playerId);
            saveRoom(room);
            bindPlayerRoom(playerId, roomId);
            log.info("[Room] 玩家={} 加入房间={}，当前人数={}", playerId, roomId, room.getPlayerIds().size());

            broadcast(roomId, "PLAYER_JOINED", Map.of("playerId", playerId, "playerCount", room.getPlayerIds().size()));
        } finally {
            lock.unlock();
        }
    }

    // ── 角色选择 ──────────────────────────────────────────────

    /** 玩家选择角色（仅 WAITING 阶段，每个角色唯一） */
    public void selectCharacter(String roomId, Long playerId, String characterType) {
        if (!ALL_CHARACTERS.contains(characterType)) {
            throw new IllegalArgumentException("无效的角色类型: " + characterType);
        }
        GameRoom room = getRoom(roomId);
        if (room.getState() != GameState.WAITING) {
            throw new IllegalStateException("游戏已开始，无法选择角色");
        }
        if (!room.getPlayerIds().contains(playerId)) {
            throw new IllegalStateException("你不在该房间中");
        }
        room.getPlayerCharacters().put(String.valueOf(playerId), characterType);
        saveRoom(room);
        log.info("[Room] 玩家={} 在房间={} 选择角色={}", playerId, roomId, characterType);
        broadcast(roomId, "CHARACTER_UPDATE", Map.of("playerCharacters", room.getPlayerCharacters()));
    }

    // ── 玩家状态 Redis 操作 ──────────────────────────────────

    private long getPlayerCoins(String roomId, String pidStr) {
        String val = (String) redis.opsForHash().get(String.format(STATE_KEY, roomId, pidStr), "coins");
        return val != null ? Long.parseLong(val) : 0L;
    }

    private void setPlayerCoins(String roomId, String pidStr, long coins) {
        redis.opsForHash().put(String.format(STATE_KEY, roomId, pidStr), "coins", String.valueOf(coins));
    }

    @SneakyThrows
    public java.util.List<String> getPlayerItemList(String roomId, String pidStr) {
        String json = (String) redis.opsForHash().get(String.format(STATE_KEY, roomId, pidStr), "items");
        if (json == null || json.isBlank()) return new java.util.ArrayList<>();
        return objectMapper.readValue(json, new TypeReference<java.util.List<String>>() {});
    }

    @SneakyThrows
    public void removePlayerItem(String roomId, String pidStr, String itemType) {
        java.util.List<String> items = getPlayerItemList(roomId, pidStr);
        if (items.remove(itemType)) {
            redis.opsForHash().put(String.format(STATE_KEY, roomId, pidStr), "items",
                    objectMapper.writeValueAsString(items));
        }
    }

    /** 初始化玩家游戏状态（游戏开始时调用，同时处理准备阶段提交的购买清单） */
    @SneakyThrows
    private void initPlayerStates(GameRoom room) {
        long initialCoins = room.getConfig().getInitialCoins();
        Map<String, List<String>> pendingPurchases = room.getPendingPurchases();
        for (Long pid : room.getPlayerIds()) {
            String pidStr = String.valueOf(pid);
            String character = room.getPlayerCharacters().get(pidStr);

            // 处理购买清单：扣除金币 + 写入道具
            long coins = initialCoins;
            List<String> items = new java.util.ArrayList<>();
            if (pendingPurchases != null && pendingPurchases.containsKey(pidStr)) {
                for (String itemType : pendingPurchases.get(pidStr)) {
                    try {
                        coins -= ItemType.valueOf(itemType).price;
                        items.add(itemType);
                    } catch (IllegalArgumentException e) {
                        log.warn("[Shop] 无效道具类型={} 玩家={} 已忽略", itemType, pidStr);
                    }
                }
            }

            Map<String, String> state = new java.util.LinkedHashMap<>();
            state.put("character", character != null ? character : "");
            state.put("coins", String.valueOf(coins));
            state.put("hasBidThisRound", "false");
            state.put("skillUsed", "false");
            state.put("items", objectMapper.writeValueAsString(items));
            redis.opsForHash().putAll(String.format(STATE_KEY, room.getRoomId(), pidStr), state);
            log.info("[Shop] 玩家={} 初始化 金币={} 道具={}", pidStr, coins, items);
        }
        // 清空购买清单，以免影响后续
        pendingPurchases.clear();
    }

    /** 使用道具（独立于出价，通过 WS /app/room/useItem 调用） */
    @SneakyThrows
    public Map<String, Object> useItem(String roomId, String pidStr, String itemType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemType", itemType);

        // 检查玩家是否有该道具
        java.util.List<String> items = getPlayerItemList(roomId, pidStr);
        if (!items.contains(itemType)) {
            result.put("success", false);
            result.put("message", "你没有该道具");
            return result;
        }

        // 从背包移除
        removePlayerItem(roomId, pidStr, itemType);

        ItemType type = ItemType.valueOf(itemType);
        GameRoom room = getRoom(roomId);
        int round = room.getCurrentRound();

        switch (type) {
            case DOUBLE_BID -> {
                // 存入待处理增益，出价时消耗
                redis.opsForHash().put(String.format(STATE_KEY, roomId, pidStr), "pendingBuff", "DOUBLE_BID");
                result.put("success", true);
                result.put("message", "翻倍卡已激活，本轮出价将翻倍计算");
            }
            case EXTRA_3000 -> {
                redis.opsForHash().put(String.format(STATE_KEY, roomId, pidStr), "pendingBuff", "EXTRA_3000");
                result.put("success", true);
                result.put("message", "加价券已激活，本轮出价将增加3000");
            }
            case BID_INSURANCE -> {
                redis.opsForHash().put(String.format(STATE_KEY, roomId, pidStr),
                        "insurance:round:" + round, "true");
                result.put("success", true);
                result.put("message", "保险卡已激活，未中标将退还出价金币");
            }
            case HALF_DISCOUNT -> {
                redis.opsForHash().put(String.format(STATE_KEY, roomId, pidStr),
                        "halfDiscount:round:" + round, "true");
                result.put("success", true);
                result.put("message", "截胡卡已激活，中标价将减半");
            }
            case PEEK_TOTAL -> {
                long total = room.getWarehouse().stream()
                        .mapToLong(w -> ((Number) w.get("value")).longValue()).sum();
                result.put("success", true);
                result.put("message", "仓库总价值: " + total + " 金币");
                result.put("totalValue", total);
            }
        }

        // 记录本轮道具使用（左侧面板展示）
        String itemsKey = "game:items:" + roomId + ":" + round;
        redis.opsForHash().put(itemsKey, pidStr, itemType);
        redis.expire(itemsKey, 24, TimeUnit.HOURS);

        log.info("[Item] 玩家={} 使用了道具={} round={}", pidStr, itemType, round);
        return result;
    }

    /** 消耗待处理的出价增益（翻倍卡/加价券），返回修正后的出价金额 */
    public long consumePendingBuff(String roomId, String pidStr, long amount) {
        String stateKey = String.format(STATE_KEY, roomId, pidStr);
        String buff = (String) redis.opsForHash().get(stateKey, "pendingBuff");
        if (buff == null) return amount;

        redis.opsForHash().delete(stateKey, "pendingBuff");

        return switch (buff) {
            case "DOUBLE_BID" -> {
                long doubled = amount * 2;
                log.info("[Item] 玩家={} 消耗翻倍卡 {}→{}", pidStr, amount, doubled);
                yield doubled;
            }
            case "EXTRA_3000" -> {
                long extra = amount + 3000;
                log.info("[Item] 玩家={} 消耗加价券 {}→{}", pidStr, amount, extra);
                yield extra;
            }
            default -> amount;
        };
    }

    // ── 状态转移 ──────────────────────────────────────────────

    /** 玩家取消准备（同时清除待提交的购买清单） */
    public void cancelReady(String roomId, Long playerId) {
        GameRoom room = getRoom(roomId);
        if (room.getState() != GameState.WAITING) throw new IllegalStateException("游戏已开始");
        if (!room.getPlayerIds().contains(playerId)) throw new IllegalStateException("你不在该房间中");
        room.getReadyPlayerIds().remove(playerId);
        room.getPendingPurchases().remove(String.valueOf(playerId));
        saveRoom(room);
        log.info("[Room] 玩家={} 取消准备 roomId={}", playerId, roomId);
        broadcast(roomId, "READY_UPDATE", Map.of(
                "readyPlayerIds", room.getReadyPlayerIds(),
                "playerIds", room.getPlayerIds()));
    }

    /** 玩家准备（可附带购买清单，在准备阶段一次提交） */
    @SneakyThrows
    public void setReady(String roomId, Long playerId, List<String> purchases) {
        GameRoom room = getRoom(roomId);
        if (room.getState() != GameState.WAITING) throw new IllegalStateException("游戏已开始");
        if (!room.getPlayerIds().contains(playerId)) throw new IllegalStateException("你不在该房间中");

        // 校验并存储购买清单
        if (purchases != null && !purchases.isEmpty()) {
            long totalCost = 0;
            for (String itemType : purchases) {
                try {
                    totalCost += ItemType.valueOf(itemType).price;
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("无效道具类型: " + itemType);
                }
            }
            long initialCoins = room.getConfig().getInitialCoins();
            if (totalCost > initialCoins) {
                throw new IllegalStateException("购买总价(" + totalCost + ")超过初始金币(" + initialCoins + ")");
            }
            room.getPendingPurchases().put(String.valueOf(playerId), purchases);
            log.info("[Shop] 玩家={} 提交购买清单={} 总价={}", playerId, purchases, totalCost);
        }

        room.getReadyPlayerIds().add(playerId);
        saveRoom(room);
        log.info("[Room] 玩家={} 准备 roomId={} 已准备={}/{}", playerId, roomId,
                room.getReadyPlayerIds().size(), room.getPlayerIds().size());
        broadcast(roomId, "READY_UPDATE", Map.of(
                "readyPlayerIds", room.getReadyPlayerIds(),
                "playerIds", room.getPlayerIds()));
    }

    /** 房主更新本局配置（仅 WAITING 阶段） */
    public void updateRoomConfig(String roomId, Long requesterId, GameConfig newConfig) {
        GameRoom room = getRoom(roomId);
        if (!room.getHostId().equals(requesterId)) throw new IllegalStateException("只有房主可修改配置");
        if (room.getState() != GameState.WAITING) throw new IllegalStateException("游戏已开始，无法修改配置");
        room.setConfig(newConfig);
        saveRoom(room);
        log.info("[Room] roomId={} 房主={} 更新配置", roomId, requesterId);
    }

    /** 开始游戏（所有人就绪后由房主触发） */
    public void startGame(String roomId, Long requesterId) {
        GameRoom room = getRoom(roomId);
        if (!room.getHostId().equals(requesterId)) {
            throw new IllegalStateException("只有房主可以开始游戏");
        }
        if (room.getState() != GameState.WAITING) {
            throw new IllegalStateException("游戏已开始");
        }
        // 所有非房主玩家必须准备
        boolean allReady = room.getPlayerIds().stream()
                .filter(pid -> !pid.equals(requesterId))
                .allMatch(pid -> room.getReadyPlayerIds().contains(pid));
        if (!allReady) {
            throw new IllegalStateException("还有玩家未准备");
        }
        // 所有人都必须选择角色
        for (Long pid : room.getPlayerIds()) {
            if (room.getPlayerCharacters() == null || !room.getPlayerCharacters().containsKey(String.valueOf(pid))) {
                throw new IllegalStateException("玩家 " + pid + " 未选择角色");
            }
        }
        log.info("[Room] 游戏开始 roomId={} 玩家数={}", (Object) roomId, (Object) room.getPlayerIds().size());
        // 游戏开始时生成仓库，确保使用最终确定的配置
        room.setWarehouse(rngManager.generate(room.getConfig()));
        saveRoom(room);
        // 初始化每个玩家的游戏状态（金币、道具、角色）
        initPlayerStates(room);
        log.info("[Room] 仓库已生成 roomId={} 物品数={}", roomId, room.getWarehouse().size());
        startTimes.put(roomId, LocalDateTime.now());
        enterSkillPhase(roomId);
    }

    /**
     * 房主踢出玩家（仅 WAITING 阶段）
     * 被踢玩家从房间移除，断开 player-room 映射，并广播更新
     */
    @SneakyThrows
    public void kickPlayer(String roomId, Long hostId, Long targetId) {
        if (hostId.equals(targetId)) throw new IllegalArgumentException("不能踢自己");
        GameRoom room = getRoom(roomId);
        if (!room.getHostId().equals(hostId)) throw new IllegalStateException("只有房主可以踢人");
        if (room.getState() != GameState.WAITING) throw new IllegalStateException("游戏已开始，无法踢人");
        if (!room.getPlayerIds().contains(targetId)) throw new IllegalArgumentException("该玩家不在房间中");

        String targetStr = String.valueOf(targetId);
        room.getPlayerIds().remove(targetId);
        room.getReadyPlayerIds().remove(targetId);
        room.getPlayerCharacters().remove(targetStr);
        room.getPendingPurchases().remove(targetStr);
        saveRoom(room);
        // 解绑 player-room 映射
        redis.delete(PLAYER_ROOM_KEY + targetId);
        log.info("[Room] 房主={} 踢出玩家={} roomId={}", hostId, targetId, roomId);

        broadcast(roomId, "PLAYER_JOINED", Map.of(
                "playerId", targetId,
                "playerCount", room.getPlayerIds().size(),
                "kicked", true));
    }

    /** 进入技能阶段 */
    public void enterSkillPhase(String roomId) {
        GameRoom room = getRoom(roomId);
        room.setCurrentRound(room.getCurrentRound() + 1);

        // 检查是否超过最大轮数（优先使用 totalRounds，否则用速胜轮+决战轮）
        int maxRounds = room.getConfig().getTotalRounds() != null
                ? room.getConfig().getTotalRounds()
                : room.getConfig().getSpeedWinRounds() + room.getConfig().getFinalRounds();
        if (room.getCurrentRound() > maxRounds) {
            log.info("[Room] roomId={} 超过最大轮数({})，强制结束游戏", roomId, maxRounds);
            broadcast(roomId, "GAME_FORCE_END", Map.of("round", room.getCurrentRound(), "reason", "超过最大轮数"));
            finishGame(roomId);
            return;
        }
        setState(roomId, room, GameState.SKILL_PHASE);
        log.info("[Room] roomId={} 第{}轮 → SKILL_PHASE", (Object) roomId, (Object) room.getCurrentRound());

        broadcast(roomId, "SKILL_PHASE_START", Map.of("round", room.getCurrentRound()));

        timerService.startSkillTimer(roomId, room.getConfig().getSkillPhaseSecs(),
                () -> enterBidding(roomId));
    }

    /** 进入出价阶段 */
    public void enterBidding(String roomId) {
        GameRoom room = getRoom(roomId);

        // 重置所有玩家本轮出价标记，避免上轮残留影响断线重连判断
        for (Long pid : room.getPlayerIds()) {
            String stateKey = String.format(STATE_KEY, roomId, pid);
            redis.opsForHash().put(stateKey, "hasBidThisRound", "false");
        }

        long deadline = System.currentTimeMillis() + room.getConfig().getBidTimeSecs() * 1000L;
        room.setRoundDeadline(deadline);
        setState(roomId, room, GameState.BIDDING);
        log.info("[Room] roomId={} 第{}轮 → BIDDING deadline={}", roomId, room.getCurrentRound(),(Object) deadline);

        broadcast(roomId, "ROUND_START", Map.of(
                "round", room.getCurrentRound(),
                "deadlineTs", deadline));

        timerService.startBidTimer(roomId, room.getConfig().getBidTimeSecs(),
                () -> enterGracePeriod(roomId));
    }

    /** 进入 Grace Period（对客户端透明，UI 显示"等待结果"） */
    public void enterGracePeriod(String roomId) {
        GameRoom room = getRoom(roomId);
        setState(roomId, room, GameState.GRACE_PERIOD);
        log.info("[Room] roomId={} 第{}轮 → GRACE_PERIOD ({}ms)", (Object) roomId, (Object) room.getCurrentRound(), (Object) room.getConfig().getGracePeriodMs());

        timerService.startGraceTimer(roomId, room.getConfig().getGracePeriodMs(),
                () -> triggerEvaluation(roomId));
    }

    /** 触发判定（由 TimerService 回调，或所有人出价后提前触发） */
    @SneakyThrows
    public void triggerEvaluation(String roomId) {
        RLock lock = redisson.getLock("lock:eval:" + roomId);
        lock.lock(5, TimeUnit.SECONDS);
        try {
            GameRoom room = getRoom(roomId);
            if (room.getState() == GameState.EVALUATING || room.getState() == GameState.FINISHED) return;
            setState(roomId, room, GameState.EVALUATING);
            log.info("[Room] roomId={} 第{}轮 → EVALUATING", (Object) roomId, (Object) room.getCurrentRound());

            // 读取本轮出价
            String bidKey = String.format(BIDS_KEY, roomId, room.getCurrentRound());
            Map<Object, Object> rawBids = redis.opsForHash().entries(bidKey);
            Map<String, Long> bids = new LinkedHashMap<>();
            // 未出价的玩家视为弃权（-1）
            for (Long pid : room.getPlayerIds()) {
                String pidStr = String.valueOf(pid);
                Object val = rawBids.get(pidStr);
                bids.put(pidStr, val != null ? Long.parseLong((String) val) : -1L);
            }

            // 读取本轮道具使用情况
            String itemsKey = "game:items:" + roomId + ":" + room.getCurrentRound();
            Map<Object, Object> roundItems = redis.opsForHash().entries(itemsKey);
            Map<String, String> items = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : roundItems.entrySet()) {
                items.put((String) e.getKey(), (String) e.getValue());
            }

            // 广播所有出价信息（供左侧面板显示）
            Map<String, Object> allBidsPayload = new LinkedHashMap<>();
            if (Boolean.TRUE.equals(room.getConfig().getBlindBidding())) {
                // 暗拍：按出价排序显示排名（不含弃权玩家）
                List<String> ranking = bids.entrySet().stream()
                        .filter(e -> e.getValue() >= 0)
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(Map.Entry::getKey)
                        .toList();
                allBidsPayload.put("ranking", ranking);
            } else {
                // 明拍：显示实际出价
                allBidsPayload.put("bids", bids);
            }
            allBidsPayload.put("items", items);
            broadcast(roomId, "ALL_BIDS", allBidsPayload);

            EvaluationResult result = bidEvaluator.evaluate(bids, room.getConfig(), room.getCurrentRound());

            // 处理道具效果：保险卡（未中标退钱）& 截胡卡（中标价减半）
            long finalHighestBid = result.getHighestBid();
            String winnerPid = result.getWinnerId();
            // 截胡卡：胜者中标价减半
            if (winnerPid != null) {
                String discountKey = "halfDiscount:round:" + room.getCurrentRound();
                String hasDiscount = (String) redis.opsForHash().get(
                        String.format(STATE_KEY, roomId, winnerPid), discountKey);
                if ("true".equals(hasDiscount)) {
                    finalHighestBid = finalHighestBid / 2;
                    log.info("[Item] 玩家={} 使用截胡卡，中标价减半为 {}", winnerPid, finalHighestBid);
                    result.setHighestBid(finalHighestBid);
                }
            }
            // 保险卡：所有未中标的保险用户退钱
            String insuranceKey = "insurance:round:" + room.getCurrentRound();
            for (Map.Entry<String, Long> e : bids.entrySet()) {
                String pidStr = e.getKey();
                if (pidStr.equals(winnerPid)) continue; // 胜者不退
                String hasInsurance = (String) redis.opsForHash().get(
                        String.format(STATE_KEY, roomId, pidStr), insuranceKey);
                if ("true".equals(hasInsurance) && e.getValue() > 0) {
                    long refund = e.getValue();
                    long current = getPlayerCoins(roomId, pidStr);
                    setPlayerCoins(roomId, pidStr, current + refund);
                    log.info("[Item] 玩家={} 保险卡退款 {}", pidStr, refund);
                }
            }

            // 下发模糊/精确反馈
            for (Long pid : room.getPlayerIds()) {
                String pidStr = String.valueOf(pid);
                if (Boolean.TRUE.equals(room.getConfig().getFuzzyFeedback())) {
                    BidFeedback fb = infoBroker.generateFeedback(pidStr, result.getBids(), result);
                    log.info("[WS] 私信 playerId={} /queue/feedback type=BID_FEEDBACK feedback={}", pidStr, fb);
                    messaging.convertAndSendToUser(pidStr, "/queue/feedback",
                            Map.of("type", "BID_FEEDBACK", "round", room.getCurrentRound(), "feedback", fb));
                } else {
                    log.info("[WS] 私信 playerId={} /queue/feedback type=BID_FEEDBACK highestBid={}", pidStr, infoBroker.getExactHighest(result));
                    messaging.convertAndSendToUser(pidStr, "/queue/feedback",
                            Map.of("type", "BID_FEEDBACK", "round", room.getCurrentRound(),
                                   "highestBid", infoBroker.getExactHighest(result)));
                }
            }

            switch (result.getOutcome()) {
                case SPEED_WIN, FINAL_WIN -> {
                    timerService.cancelTimer(roomId);
                    enterRevealing(roomId);
                    revealEngine.reveal(room, result.getWinnerId(), result.getHighestBid(),
                            startTimes.getOrDefault(roomId, LocalDateTime.now()));
                    finishGame(roomId);
                    startTimes.remove(roomId);
                }
                case TIE_BREAK -> {
                    if (room.getTieBreakCount() < room.getConfig().getTieBreakRounds()) {
                        enterTieBreak(roomId);
                    } else {
                        // 超过加赛上限，均分（广播通知，不做金币转移）
                        broadcast(roomId, "TIE_FINAL", Map.of("round", room.getCurrentRound()));
                        finishGame(roomId);
                    }
                }
                case NO_WIN -> enterSkillPhase(roomId);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 进入开箱阶段 */
    public void enterRevealing(String roomId) {
        GameRoom room = getRoom(roomId);
        setState(roomId, room, GameState.REVEALING);
        log.info("[Room] roomId={} → REVEALING", roomId);
        broadcast(roomId, "REVEALING_START", Map.of());
    }

    /** 进入结算阶段 */
    public void enterSettling(String roomId) {
        GameRoom room = getRoom(roomId);
        setState(roomId, room, GameState.SETTLING);
        log.info("[Room] roomId={} → SETTLING", roomId);
    }

    /** 进入平局加赛 */
    public void enterTieBreak(String roomId) {
        GameRoom room = getRoom(roomId);
        room.setTieBreakCount(room.getTieBreakCount() + 1);

        // 清除本轮旧出价，允许玩家重新出价（因 tie-break 不递增 currentRound）
        String bidKey = String.format(BIDS_KEY, roomId, room.getCurrentRound());
        redis.delete(bidKey);
        log.info("[Room] roomId={} tie-break 清除旧出价 key={}", roomId, bidKey);

        setState(roomId, room, GameState.TIE_BREAK);
        log.info("[Room] roomId={} → TIE_BREAK 第{}次加赛", (Object) roomId, (Object) room.getTieBreakCount());
        broadcast(roomId, "TIE_BREAK", Map.of("tieBreakCount", room.getTieBreakCount()));
        enterBidding(roomId);
    }

    /** 游戏结束 */
    public void finishGame(String roomId) {
        GameRoom room = getRoom(roomId);
        setState(roomId, room, GameState.FINISHED);
        timerService.cancelTimer(roomId);
        log.info("[Room] roomId={} → FINISHED", roomId);
        broadcast(roomId, "GAME_FINISHED", Map.of());
    }

    /** 重新开始：重置游戏状态但保留房间和配置，玩家可继续准备 */
    @SneakyThrows
    public void restartGame(String roomId, Long requesterId) {
        GameRoom room = getRoom(roomId);
        if (!room.getHostId().equals(requesterId)) {
            throw new IllegalStateException("只有房主可以重新开始");
        }
        if (room.getState() != GameState.FINISHED) {
            throw new IllegalStateException("游戏未结束，无法重新开始");
        }
        timerService.cancelTimer(roomId);
        startTimes.remove(roomId);

        // 清理所有玩家的 Redis 状态
        for (Long pid : room.getPlayerIds()) {
            String stateKey = String.format(STATE_KEY, roomId, pid);
            redis.delete(stateKey);
        }
        // 清理出价和道具记录（当前局所有轮次）
        int maxRounds = room.getConfig().getTotalRounds() != null
                ? room.getConfig().getTotalRounds()
                : room.getConfig().getSpeedWinRounds() + room.getConfig().getFinalRounds();
        for (int r = 1; r <= maxRounds + 5; r++) { // +5 冗余处理 tie-break 等
            redis.delete(String.format(BIDS_KEY, roomId, r));
            redis.delete("game:items:" + roomId + ":" + r);
        }

        // 重置房间状态
        room.setCurrentRound(0);
        room.setTieBreakCount(0);
        room.setWarehouse(null);
        room.setState(GameState.WAITING);
        room.setReadyPlayerIds(new java.util.HashSet<>());
        room.setPendingPurchases(new LinkedHashMap<>());
        room.setPlayerCharacters(new LinkedHashMap<>());
        room.setRoundDeadline(0L);
        saveRoom(room);

        log.info("[Room] roomId={} 重新开始，房主={} 玩家数={}", roomId, requesterId, room.getPlayerIds().size());
        broadcast(roomId, "GAME_RESTART", Map.of("playerIds", room.getPlayerIds()));
    }

    // ── 工具方法 ──────────────────────────────────────────────

    @SneakyThrows
    public GameRoom getRoom(String roomId) {
        String json = redis.opsForValue().get(ROOM_KEY + roomId);
        if (json == null) throw new IllegalArgumentException("房间不存在: " + roomId);
        return objectMapper.readValue(json, GameRoom.class);
    }

    @SneakyThrows
    public void saveRoom(GameRoom room) {
        String json = objectMapper.writeValueAsString(room);
        redis.opsForValue().set(ROOM_KEY + room.getRoomId(), json, ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public void setState(String roomId, GameRoom room, GameState state) {
        log.debug("[Room] roomId={} 状态变更 {} → {}", roomId, room.getState(), state);
        room.setState(state);
        saveRoom(room);
    }

    private void bindPlayerRoom(Long playerId, String roomId) {
        redis.opsForValue().set(PLAYER_ROOM_KEY + playerId, roomId, ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    private void broadcast(String roomId, String type, Object payload) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", type);
        msg.put("payload", payload);
        messaging.convertAndSend("/topic/room/" + roomId, msg);
        log.info("[WS] 广播 roomId={} type={} payload={}", roomId, type, payload);
    }

    /** 每10秒扫描一次，清理已结束或空房间，防止内存泄露 */
    @Scheduled(fixedDelay = 10_000)
    public void cleanupRooms() {
        Set<String> keys = redis.keys(ROOM_KEY + "*");
        if (keys == null || keys.isEmpty()) return;
        for (String key : keys) {
            try {
                String json = redis.opsForValue().get(key);
                if (json == null) continue;
                GameRoom room = objectMapper.readValue(json, GameRoom.class);
                if (room.getState() == GameState.FINISHED
                        || room.getPlayerIds() == null
                        || room.getPlayerIds().isEmpty()) {
                    redis.delete(key);
                    log.info("[Cleanup] 删除房间 key={} state={}", key, room.getState());
                }
            } catch (Exception e) {
                log.warn("[Cleanup] 解析房间失败，强制删除 key={}", key, e);
                redis.delete(key);
            }
        }
    }
}
