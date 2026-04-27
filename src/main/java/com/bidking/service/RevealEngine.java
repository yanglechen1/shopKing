package com.bidking.service;

import com.bidking.dto.GameRoom;
import com.bidking.entity.GamePlayerRecord;
import com.bidking.entity.GameRecord;
import com.bidking.mapper.GamePlayerRecordMapper;
import com.bidking.mapper.GameRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 开箱结算引擎
 * 负责物品按品质排序揭晓、利润计算、持久化游戏记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevealEngine {

    private final GameRecordMapper gameRecordMapper;
    private final GamePlayerRecordMapper gamePlayerRecordMapper;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper;

    private static final List<String> QUALITY_ORDER =
            List.of("WHITE", "GREEN", "BLUE", "PURPLE", "GOLD", "RED");

    /**
     * 执行开箱：按品质从低到高逐件广播，然后结算
     * @param room      当前房间
     * @param winnerId  获胜玩家ID
     * @param finalBid  最终中标价
     * @param startedAt 游戏开始时间
     */
    @SneakyThrows
    public void reveal(GameRoom room, String winnerId, long finalBid, LocalDateTime startedAt) {
        List<Map<String, Object>> warehouse = room.getWarehouse();

        // 按品质从低到高排序
        List<Map<String, Object>> sorted = new ArrayList<>(warehouse);
        sorted.sort(Comparator.comparingInt(item ->
                QUALITY_ORDER.indexOf(item.get("quality"))));

        // 逐件广播（客户端根据 revealDelaySecs 控制动画节奏）
        for (int i = 0; i < sorted.size(); i++) {
            Map<String, Object> item = sorted.get(i);
            item.put("revealed", true);
            messaging.convertAndSend("/topic/room/" + room.getRoomId(),
                    Map.of("type", "ITEM_REVEALED",
                           "index", i,
                           "total", sorted.size(),
                           "item", sanitize(item)));
            log.info("[Reveal] roomId={} 揭晓第{}/{} 物品={} 品质={}",
                    room.getRoomId(), i + 1, sorted.size(),
                    item.get("name"), item.get("quality"));
        }

        // 计算仓库总价值
        long warehouseValue = warehouse.stream()
                .mapToLong(item -> ((Number) item.get("value")).longValue())
                .sum();
        long profit = warehouseValue - finalBid;

        // 持久化游戏记录
        GameRecord record = new GameRecord();
        record.setRoomId(room.getRoomId());
        record.setConfigSnapshot(objectMapper.writeValueAsString(room.getConfig()));
        record.setWinnerId(Long.valueOf(winnerId));
        record.setFinalBid(finalBid);
        record.setWarehouseValue(warehouseValue);
        record.setProfit(profit);
        record.setTotalRounds(room.getCurrentRound());
        record.setStartedAt(startedAt);
        record.setEndedAt(LocalDateTime.now());
        gameRecordMapper.insert(record);

        // 持久化每位玩家记录
        for (Long playerId : room.getPlayerIds()) {
            GamePlayerRecord pr = new GamePlayerRecord();
            pr.setGameRecordId(record.getId());
            pr.setPlayerId(playerId);
            pr.setProfit(String.valueOf(playerId).equals(winnerId) ? profit : 0L);
            gamePlayerRecordMapper.insert(pr);
        }

        // 广播结算结果
        messaging.convertAndSend("/topic/room/" + room.getRoomId(),
                Map.of("type", "GAME_SETTLED",
                       "winnerId", winnerId,
                       "finalBid", finalBid,
                       "warehouseValue", warehouseValue,
                       "profit", profit));

        log.info("[Reveal] roomId={} 结算完成 winner={} bid={} value={} profit={}",
                room.getRoomId(), winnerId, finalBid, warehouseValue, profit);
    }

    /** 移除 value 字段，防止仓库实际价值泄露给客户端 */
    private Map<String, Object> sanitize(Map<String, Object> item) {
        Map<String, Object> safe = new LinkedHashMap<>(item);
        safe.remove("value");
        return safe;
    }
}
