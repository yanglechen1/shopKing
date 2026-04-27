package com.bidking.controller;

import com.bidking.dto.BidRequest;
import com.bidking.dto.GameRoom;
import com.bidking.dto.UseItemRequest;
import com.bidking.enums.GameState;
import com.bidking.service.RateLimitService;
import com.bidking.service.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 控制器
 * 出价和重连通过 STOMP 协议处理，不受 REST @RateLimit 注解覆盖，
 * 改为方法内手动调用 {@link RateLimitService#tryAcquire(String, String)} 进行限流。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWsController {

    private final RoomManager roomManager;
    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messaging;
    private final RateLimitService rateLimitService;

    private static final String BIDS_KEY  = "game:bids:%s:%d";
    private static final String STATE_KEY = "game:player:state:%s:%s";

    /** 提交出价 /app/room/{roomId}/bid */
    @MessageMapping("/room/bid")
    public void submitBid(BidRequest req, Principal principal) {
        if (principal == null) { log.warn("[WS] 未认证用户尝试出价，已忽略"); return; }
        String playerId = principal.getName();

        // 限流检查（每60秒最多5次，SETNX已防重，此做纵深防御）
        if (!rateLimitService.tryAcquire("submitBid", playerId)) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "操作过于频繁，请稍后再试"));
            return;
        }

        GameRoom room = roomManager.getRoom(req.getRoomId());

        // 只在 BIDDING 或 GRACE_PERIOD 阶段接受出价
        if (room.getState() != GameState.BIDDING && room.getState() != GameState.GRACE_PERIOD) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "当前阶段不接受出价"));
            return;
        }

        // Grace Period 截止判定
        long graceCutoff = room.getRoundDeadline() + room.getConfig().getGracePeriodMs();
        if (System.currentTimeMillis() > graceCutoff) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "出价已截止"));
            return;
        }

        // 金额校验
        if (req.getAmount() < room.getConfig().getMinBid()) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "出价低于最低限额"));
            return;
        }

        // SETNX 防重复出价（先占位，后续覆盖实际金额）
        String bidKey = String.format(BIDS_KEY, req.getRoomId(), room.getCurrentRound());
        Boolean set = redis.opsForHash().putIfAbsent(bidKey, playerId, "0");
        if (Boolean.FALSE.equals(set)) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "本轮已出价，不可修改"));
            return;
        }
        redis.expire(bidKey, 24, TimeUnit.HOURS);

        // 检查是否有待处理增益（在消耗前记录，用于左侧面板展示）
        String stateKeyCheck = String.format(STATE_KEY, req.getRoomId(), playerId);
        String pendingBuff = (String) redis.opsForHash().get(stateKeyCheck, "pendingBuff");

        // 处理道具增益（独立使用道具时存储的 pendingBuff）
        long effectiveAmount = req.getAmount();
        String usedItem = null;
        effectiveAmount = roomManager.consumePendingBuff(req.getRoomId(), playerId, effectiveAmount);

        // 记录本轮道具使用情况（左侧面板多轮展示用）
        if (pendingBuff != null) {
            String itemsKey = "game:items:" + req.getRoomId() + ":" + room.getCurrentRound();
            redis.opsForHash().put(itemsKey, playerId, pendingBuff);
            redis.expire(itemsKey, 24, java.util.concurrent.TimeUnit.HOURS);
            usedItem = pendingBuff;
        }

        // 写入实际出价金额（可能被道具修改过）
        redis.opsForHash().put(bidKey, playerId, String.valueOf(effectiveAmount));

        // 标记本轮已出价
        redis.opsForHash().put(String.format(STATE_KEY, req.getRoomId(), playerId),
                "hasBidThisRound", "true");

        log.info("[WS] 玩家={} 房间={} 第{}轮 出价={}{}", playerId, req.getRoomId(),
                room.getCurrentRound(), effectiveAmount,
                usedItem != null ? " 道具=" + usedItem : "");

        messaging.convertAndSendToUser(playerId, "/queue/bid-ack",
                Map.of("round", room.getCurrentRound(), "amount", effectiveAmount));

        // 广播哪位玩家出价了（供左侧面板显示状态，count供前端计算已出价人数）
        long bidCount = redis.opsForHash().size(bidKey);
        messaging.convertAndSend("/topic/room/" + req.getRoomId(),
                Map.of("type", "BID_PLACED", "playerId", playerId,
                       "count", bidCount, "total", room.getPlayerIds().size()));

        // 所有人都出价则提前触发判定
        if (bidCount >= room.getPlayerIds().size()) {
            log.info("[WS] 房间={} 所有人出价完毕，提前触发判定", req.getRoomId());
            roomManager.triggerEvaluation(req.getRoomId());
        }
    }

    /** 断线重连 /app/reconnect */
    @MessageMapping("/reconnect")
    public void reconnect(@Payload(required = false) Map<String, String> payload,
                          Principal principal) {
        if (principal == null) { log.warn("[WS] 未认证用户尝试重连"); return; }
        String playerId = principal.getName();

        // 限流检查（每60秒最多5次）
        if (!rateLimitService.tryAcquire("reconnect", playerId)) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "操作过于频繁，请稍后再试"));
            return;
        }

        String currentRoomId = redis.opsForValue().get("game:player:room:" + playerId);

        if (currentRoomId == null) {
            messaging.convertAndSendToUser(playerId, "/queue/reconnect",
                    Map.of("action", "TO_LOBBY"));
            return;
        }

        // 若客户端传了 roomId，只有匹配时才恢复（防止旧局污染新局）
        String requestedRoomId = payload != null ? payload.get("roomId") : null;
        if (requestedRoomId != null && !requestedRoomId.equals(currentRoomId)) {
            log.info("[WS] 玩家={} 重连 roomId 不匹配 requested={} current={}", playerId, requestedRoomId, currentRoomId);
            return;
        }

        GameRoom room = roomManager.getRoom(currentRoomId);

        // WAITING 阶段游戏未开始，无需重连快照
        if (room.getState() == GameState.WAITING) return;

        String bidKey = String.format(BIDS_KEY, currentRoomId, room.getCurrentRound());
        boolean hasBid = redis.opsForHash().hasKey(bidKey, playerId);

        // 收集本轮已出价的玩家ID列表（供前端恢复左侧面板状态）
        Map<Object, Object> existingBids = redis.opsForHash().entries(bidKey);
        java.util.List<String> bidderIds = new java.util.ArrayList<>();
        for (Object key : existingBids.keySet()) {
            String val = (String) existingBids.get(key);
            // "0" 是 SETNX 占位符，实际金额还未写入（极端竞争条件）
            if (val != null && !"0".equals(val)) {
                bidderIds.add((String) key);
            }
        }

        log.info("[WS] 玩家={} 重连 房间={} 状态={} 已出价={} 本轮已出价人数={}", playerId, currentRoomId, room.getState(), hasBid, bidderIds.size());
        log.info("[WS] 私信 playerId={} /queue/reconnect action=RESUME state={}", playerId, room.getState());

        // 读取玩家背包信息，供前端恢复道具/金币显示
        String stateKey = String.format(STATE_KEY, currentRoomId, playerId);
        String coinsStr = (String) redis.opsForHash().get(stateKey, "coins");
        String itemsJson = (String) redis.opsForHash().get(stateKey, "items");
        long coins = coinsStr != null ? Long.parseLong(coinsStr) : 0;
        java.util.List<String> items = new java.util.ArrayList<>();
        if (itemsJson != null && !itemsJson.isBlank()) {
            items = roomManager.getPlayerItemList(currentRoomId, playerId);
        }

        messaging.convertAndSendToUser(playerId, "/queue/reconnect", Map.of(
                "action", "RESUME",
                "state", room.getState(),
                "round", room.getCurrentRound(),
                "deadlineTs", room.getRoundDeadline(),
                "hasBidThisRound", hasBid,
                "bidderIds", bidderIds,
                "coins", coins,
                "items", items
        ));
    }

    /** 使用道具 /app/room/useItem（独立于出价，先使用道具获取效果，再出价） */
    @MessageMapping("/room/useItem")
    public void useItem(UseItemRequest req, Principal principal) {
        if (principal == null) { log.warn("[WS] 未认证用户尝试使用道具"); return; }
        String playerId = principal.getName();

        // 限流检查
        if (!rateLimitService.tryAcquire("useItem", playerId)) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "操作过于频繁，请稍后再试"));
            return;
        }

        GameRoom room = roomManager.getRoom(req.getRoomId());

        // 只在游戏中可使用道具
        if (room.getState() == GameState.WAITING || room.getState() == GameState.FINISHED) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "当前阶段不可使用道具"));
            return;
        }

        if (req.getItemType() == null || req.getItemType().isEmpty()) {
            messaging.convertAndSendToUser(playerId, "/queue/error",
                    Map.of("error", "未指定道具类型"));
            return;
        }

        Map<String, Object> result = roomManager.useItem(req.getRoomId(), playerId, req.getItemType());
        messaging.convertAndSendToUser(playerId, "/queue/item-result", result);
        log.info("[WS] 玩家={} 使用道具={} 结果={}", playerId, req.getItemType(), result);
    }

}
