package com.bidking.service;

import com.bidking.dto.EvaluationResult;
import com.bidking.enums.BidFeedback;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InfoBroker {

    /**
     * 生成下发给指定玩家的模糊反馈，绝不包含他人出价金额
     */
    public BidFeedback generateFeedback(String playerId,
                                        Map<String, Long> allBids,
                                        EvaluationResult result) {
        Long myBid = allBids.get(playerId);
        if (myBid == null || myBid < 0) return BidFeedback.FAR_BEHIND;

        long p1 = result.getHighestBid();
        if (myBid == p1 && result.isUnique()) return BidFeedback.LEADING;
        if (myBid >= p1 * 0.9)              return BidFeedback.CLOSE;
        if (myBid >= p1 * 0.5)              return BidFeedback.BEHIND;
        return BidFeedback.FAR_BEHIND;
    }

    /**
     * 精确反馈：直接返回最高价（fuzzyFeedback=false 时使用）
     */
    public long getExactHighest(EvaluationResult result) {
        return result.getHighestBid();
    }
}
