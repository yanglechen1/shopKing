package com.bidking.service;

import com.bidking.dto.EvaluationResult;
import com.bidking.dto.EvaluationResult.Outcome;
import com.bidking.entity.GameConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidEvaluator {

    private final ObjectMapper objectMapper;

    /**
     * 汇总本轮出价，判定结果
     * @param bids   Map<playerId, bidAmount>，未出价值为 -1
     * @param config 本局配置快照
     * @param round  当前轮次（1-based）
     */
    @SneakyThrows
    public EvaluationResult evaluate(Map<String, Long> bids, GameConfig config, int round) {
        // 过滤弃权（-1）
        Map<String, Long> valid = new LinkedHashMap<>();
        bids.forEach((pid, amt) -> { if (amt >= 0) valid.put(pid, amt); });

        EvaluationResult result = new EvaluationResult();
        result.setBids(valid);

        if (valid.isEmpty()) {
            result.setOutcome(Outcome.NO_WIN);
            return result;
        }

        // 排序找 P1、P2
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(valid.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        long p1 = sorted.get(0).getValue();
        long p2 = sorted.size() > 1 ? sorted.get(1).getValue() : 0L;
        String p1Id = sorted.get(0).getKey();
        boolean unique = sorted.size() == 1 || p1 > p2;

        result.setHighestBid(p1);
        result.setSecondHighestBid(p2);
        result.setUnique(unique);

        // 平局
        if (!unique) {
            result.setOutcome(Outcome.TIE_BREAK);
            log.info("[Eval] 第{}轮 平局 p1={} p2={}", round, p1, p2);
            return result;
        }

        // 决战轮（速胜窗口之后）：直接 P1 获胜
        boolean isFinalRound = round > config.getSpeedWinRounds();
        if (isFinalRound) {
            result.setOutcome(Outcome.FINAL_WIN);
            result.setWinnerId(p1Id);
            log.info("[Eval] 第{}轮 决战胜出 winner={} bid={}", round, p1Id, p1);
            return result;
        }

        // 速胜判定
        List<Double> ratios = objectMapper.readValue(config.getSpeedWinRatios(), new TypeReference<>() {});
        if (round - 1 >= ratios.size()) {
            log.error("[Eval] 速胜倍率数组长度不足 ratios.size={} round={}", ratios.size(), round);
            throw new IllegalStateException("速胜倍率配置错误，第" + round + "轮无对应倍率");
        }
        double ratio = ratios.get(round - 1);
        if (p1 >= p2 * ratio) {
            result.setOutcome(Outcome.SPEED_WIN);
            result.setWinnerId(p1Id);
            log.info("[Eval] 第{}轮 速胜 winner={} p1={} p2={} ratio={}", round, p1Id, p1, p2, ratio);
        } else {
            result.setOutcome(Outcome.NO_WIN);
            log.info("[Eval] 第{}轮 未触发速胜 p1={} p2={} ratio={}", round, p1, p2, ratio);
        }

        return result;
    }
}
