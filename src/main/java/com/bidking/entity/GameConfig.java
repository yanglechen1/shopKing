package com.bidking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 全局游戏配置（只有一行，id 固定为 1）
 * 创建房间时做快照，中途修改不影响进行中的局
 */
@Data
@TableName("game_config")
public class GameConfig {
    @TableId
    private Integer id = 1;

    private Integer playerCount = 5;        // 玩家人数 2~8
    private Long initialCoins = 10000L;     // 每人初始金币
    private Long minBid = 1L;              // 最低出价

    private Integer speedWinRounds = 4;    // 速胜窗口轮数
    private Integer finalRounds = 1;       // 决战轮数
    private Integer totalRounds = 10;      // 拍卖轮次：每局总轮数（替代 speedWinRounds+finalRounds）
    private Integer tieBreakRounds = 3;    // 平局加赛最大轮数

    private Integer skillPhaseSecs = 20;   // 技能阶段时间（秒）
    private Integer bidTimeSecs = 30;      // 出价倒计时（秒）
    private Integer gracePeriodMs = 3000;  // 出价冗余时间（毫秒）

    private String speedWinRatios;         // JSON array: [1.8,1.5,1.3,1.15]
    private Integer warehouseSizeMin = 20;
    private Integer warehouseSizeMax = 100;
    private Boolean blackBoxEnabled = true;
    private Integer revealDelaySecs = 2;
    private String qualityWeights;         // JSON object: {"COMMON":50,...}

    // ── 仓库主题与地区配置 ──────────────────────────
    /** 仓库主题: Category枚举名 / "UNKNOWN" / "RANDOM" */
    private String warehouseTheme = "RANDOM";
    /** 仓库地区: Region key / "RANDOM" */
    private String warehouseRegion = "RANDOM";
    /** Region 预设 JSON 数组，可热更新品质权重和数量范围 */
    private String warehouseRegions;
    /** Theme 预设 JSON 数组，可热更新品类权重 */
    private String warehouseThemes;

    // ── 信息透明度配置 ──────────────────────────
    /** true=暗标（看不到他人出价），false=明标（可看到他人每轮出价） */
    private Boolean blindBidding = true;
    /** true=模糊反馈（领先/靠后），false=精确反馈（显示具体金额） */
    private Boolean fuzzyFeedback = true;
}
