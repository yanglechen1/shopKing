package com.bidking.enums;

/**
 * 游戏状态机枚举
 * 状态流转只能由 RoomManager 驱动
 */
public enum GameState {
    WAITING,      // 等待玩家加入
    SKILL_PHASE,  // 技能/道具决策阶段
    BIDDING,      // 暗标出价阶段
    GRACE_PERIOD, // 出价冗余时间（对玩家透明）
    EVALUATING,   // 服务端判定（瞬时）
    REVEALING,    // 开箱揭晓
    SETTLING,     // 结算
    TIE_BREAK,    // 平局加赛
    FINISHED      // 游戏结束
}
