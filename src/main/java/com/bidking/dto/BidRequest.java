package com.bidking.dto;

import lombok.Data;

@Data
public class BidRequest {
    private String roomId;
    private long amount;
    private long clientTs; // 仅日志用，判定以服务端为准
}
