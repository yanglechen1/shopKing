package com.bidking.dto;

import com.bidking.entity.GameConfig;
import com.bidking.enums.GameState;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 房间实时状态（存储在 Redis，不持久化到 MySQL）
 * 包含游戏进行中的所有动态数据
 */
@Data
public class GameRoom {

    /** 房间唯一ID（UUID） */
    private String roomId;

    /** 当前游戏状态，由 RoomManager 统一驱动 */
    private GameState state = GameState.WAITING;

    /** 当前轮次（1-based：第1轮=1，游戏初始即为1，无 0 值语义） */
    private int currentRound = 1;

    /** 本轮出价截止时间戳（Unix ms），客户端用于倒计时同步 */
    private long roundDeadline = 0L;

    /** 创建房间时的 GameConfig 快照，游戏中途不变 */
    private GameConfig config;

    /** 房主玩家ID */
    private Long hostId;

    /** 已加入的玩家ID列表，顺序即加入顺序 */
    private List<Long> playerIds;

    /** 玩家 → 角色类型映射，准备阶段选择（key = playerId 的字符串） */
    private Map<String, String> playerCharacters = new java.util.LinkedHashMap<>();

    /** 仓库物品列表（含实际价值），绝不下发给客户端 */
    private List<Map<String, Object>> warehouse;

    /** 已准备的玩家ID集合（WAITING 阶段使用） */
    private Set<Long> readyPlayerIds = new HashSet<>();

    /** 平局加赛已进行次数，超过 config.tieBreakRounds 则均分 */
    private int tieBreakCount = 0;

    /**
     * 玩家准备时提交的购买清单 key=playerId字符串, value=购买的道具类型列表
     * 仅 WAITING 阶段有效，startGame() 中会处理并清空
     */
    private Map<String, List<String>> pendingPurchases = new java.util.LinkedHashMap<>();
}
