package com.bidking.dto;

import lombok.Data;

import java.util.Map;

@Data
public class EvaluationResult {

    public enum Outcome { SPEED_WIN, FINAL_WIN, TIE_BREAK, NO_WIN }

    private Outcome outcome;
    private String winnerId;       // 速胜/决战胜者，平局/未触发为 null
    private long highestBid;
    private long secondHighestBid;
    private boolean unique;        // 最高价是否唯一
    private Map<String, Long> bids; // 本轮所有有效出价
}
