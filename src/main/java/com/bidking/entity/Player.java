package com.bidking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 玩家账号表
 * 存储注册信息、累计金币和积分
 */
@Data
@TableName("player")
public class Player {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名，唯一 */
    private String username;

    /** 显示昵称 */
    private String nickname;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 历史累计金币（所有局结算后累加） */
    private Long totalCoins = 0L;

    /** 历史累计积分 */
    private Integer totalScore = 0;

    private LocalDateTime createdAt;
}
