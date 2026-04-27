package com.bidking.dto;

import lombok.Data;

/**
 * 使用道具请求
 * 玩家在游戏中使用道具时通过 WS 发送
 */
@Data
public class UseItemRequest {
    private String roomId;
    private String itemType; // 使用的道具类型
}
