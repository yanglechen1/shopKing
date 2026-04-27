package com.bidking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 玩家游戏参与记录
 * 每局每个玩家一行，记录本局出价、花费和利润
 */
@Data
@TableName("game_player_record")
public class GamePlayerRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 game_record.id */
    private Long gameRecordId;

    /** 关联 player.id */
    private Long playerId;

    /** 本局使用的角色类型 */
    private String characterType;

    /** 最终出价金额 */
    private Long finalBid = 0L;

    /** 花费的金币（道具等消耗） */
    private Long coinsSpent = 0L;

    /** 本局利润（仅获胜者有正值） */
    private Long profit = 0L;

    /** 本局排名 */
    private Integer rank;
}
