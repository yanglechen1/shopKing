package com.bidking.dto;

import lombok.Data;

@Data
public class BidRequest {
    private String roomId;
    private long amount;
    private String itemType; // 使用的道具类型（null=不使用道具），每次出价限用一个
    private long clientTs; // 仅日志用，判定以服务端为准
}
