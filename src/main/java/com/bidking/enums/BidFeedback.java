package com.bidking.enums;

/**
 * 出价模糊反馈（不暴露具体金额，只告知相对位置）
 */
public enum BidFeedback {
    LEADING,   // 领先（当前最高价且唯一）
    CLOSE,     // 靠后（≥最高价90%）
    BEHIND,    // 落后（≥最高价50%）
    FAR_BEHIND // 远落后（<最高价50%）
}
