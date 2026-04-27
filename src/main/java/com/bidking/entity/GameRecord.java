package com.bidking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 游戏记录表
 * 每局游戏结束后写入一行，保存完整结果
 */
@Data
@TableName("game_record")
public class GameRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房间UUID */
    private String roomId;

    /** 本局 GameConfig 快照（JSON），与进行中配置解耦 */
    private String configSnapshot;

    /** 获胜玩家ID */
    private Long winnerId;

    /** 最终中标价 */
    private Long finalBid;

    /** 仓库物品总实际价值 */
    private Long warehouseValue;

    /** 获胜者利润 = warehouseValue - finalBid */
    private Long profit;

    /** 本局总轮次 */
    private Integer totalRounds;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
