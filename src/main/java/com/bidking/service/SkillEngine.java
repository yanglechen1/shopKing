package com.bidking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 角色技能执行引擎 v2
 * 所有技能聚焦于物品信息探查，不暴露实际价值
 *
 * 技能设计：
 * - 老头：随机窥探一件物品（名称+品质+品类）
 * - 艾莎：品类扫描（指定品类的所有物品名称+品质）
 * - 伊森：最强信号（最高品质物品的轮廓：品质+品类，不暴露名称）
 * - 石油哥：品质透视（各品质等级数量分布）
 * - 索嗨：深度探查（随机一件物品完整信息+模糊价值等级）
 * - 伊莎贝拉：顶级感知（最高品质的名称+数量）
 */
@Slf4j
@Service
public class SkillEngine {

    /**
     * 执行技能，返回信息给请求玩家
     * @param characterType 角色类型
     * @param warehouse     仓库完整数据（服务端持有）
     * @param round         当前轮次
     * @param category      品类参数（仅 AISHA 使用）
     */
    public Map<String, Object> useSkill(String characterType,
                                        List<Map<String, Object>> warehouse,
                                        int round,
                                        String category) {
        return switch (characterType.toUpperCase()) {
            case "LAOTOU"    -> laotouSkill(warehouse);
            case "AISHA"     -> aishaSkill(warehouse, category);
            case "ETHAN"     -> ethanSkill(warehouse);
            case "OILMAN"    -> oilmanSkill(warehouse);
            case "SOHAI"     -> sohaiSkill(warehouse);
            case "ISABELLA"  -> isabellaSkill(warehouse);
            default -> Map.of("error", "未知角色: " + characterType);
        };
    }

    /** 老头：随机窥探一件物品（揭示名称+品质+品类） */
    private Map<String, Object> laotouSkill(List<Map<String, Object>> warehouse) {
        if (warehouse.isEmpty()) return Map.of("type", "PEEK_RANDOM", "found", false);
        Map<String, Object> item = warehouse.get(ThreadLocalRandom.current().nextInt(warehouse.size()));
        log.info("[Skill] 老头 随机窥探 item={}", item.get("name"));
        return Map.of(
                "type", "PEEK_RANDOM",
                "name", item.get("name"),
                "quality", item.get("quality"),
                "category", item.get("category")
        );
    }

    /** 艾莎：品类扫描 — 查看指定品类的所有物品名称+品质 */
    private Map<String, Object> aishaSkill(List<Map<String, Object>> warehouse, String category) {
        String cat = (category != null) ? category.toUpperCase() : "";
        if (!Set.of("FURNITURE", "DIGITAL", "ANTIQUE", "BOOK", "JEWELRY", "FOOD", "ELECTRONICS", "ART", "MUSICAL", "WEAPON").contains(cat)) {
            return Map.of("type", "CATEGORY_SCAN", "error", "请指定有效品类");
        }
        List<Map<String, Object>> filtered = warehouse.stream()
                .filter(item -> cat.equals(item.get("category")))
                .toList();
        List<Map<String, Object>> items = filtered.stream()
                .map(item -> Map.of("name", item.get("name"), "quality", item.get("quality")))
                .toList();
        log.info("[Skill] 艾莎 品类扫描 category={} count={}", cat, items.size());
        return Map.of(
                "type", "CATEGORY_SCAN",
                "category", cat,
                "count", items.size(),
                "items", items
        );
    }

    /** 伊森：最强信号 — 展示最高品质物品的轮廓（品质+品类，不暴露名称和价值） */
    private Map<String, Object> ethanSkill(List<Map<String, Object>> warehouse) {
        if (warehouse.isEmpty()) return Map.of("type", "TOP_OUTLINE", "found", false);
        Map<String, Object> top = warehouse.stream()
                .max(Comparator.comparingInt(item -> qualityOrder((String) item.get("quality"))))
                .orElse(null);
        if (top == null) return Map.of("type", "TOP_OUTLINE", "found", false);
        log.info("[Skill] 伊森 最强信号 quality={} category={}", top.get("quality"), top.get("category"));
        return Map.of(
                "type", "TOP_OUTLINE",
                "quality", top.get("quality"),
                "category", top.get("category")
        );
    }

    /** 石油哥：品质透视 — 显示各品质等级的数量分布 */
    private Map<String, Object> oilmanSkill(List<Map<String, Object>> warehouse) {
        Map<String, Long> dist = warehouse.stream()
                .collect(Collectors.groupingBy(
                        item -> (String) item.get("quality"),
                        LinkedHashMap::new,
                        Collectors.counting()));
        for (String q : List.of("WHITE", "GREEN", "BLUE", "PURPLE", "GOLD", "RED")) {
            dist.putIfAbsent(q, 0L);
        }
        log.info("[Skill] 石油哥 品质分布={}", dist);
        return Map.of(
                "type", "QUALITY_DISTRIBUTION",
                "distribution", dist
        );
    }

    /** 索嗨：深度探查 — 随机揭示一件物品的完整信息+模糊价值等级 */
    private Map<String, Object> sohaiSkill(List<Map<String, Object>> warehouse) {
        if (warehouse.isEmpty()) return Map.of("type", "DEEP_PEEK", "found", false);
        Map<String, Object> item = warehouse.get(ThreadLocalRandom.current().nextInt(warehouse.size()));
        String valueTier = calculateValueTier(item, warehouse);
        log.info("[Skill] 索嗨 深度探查 name={} valueTier={}", item.get("name"), valueTier);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "DEEP_PEEK");
        result.put("name", item.get("name"));
        result.put("quality", item.get("quality"));
        result.put("category", item.get("category"));
        result.put("valueTier", valueTier);
        result.put("isBlackBox", item.get("isBlackBox"));
        return result;
    }

    /** 伊莎贝拉：顶级感知 — 查看最高品质物品的名称+该品质共有几件 */
    private Map<String, Object> isabellaSkill(List<Map<String, Object>> warehouse) {
        if (warehouse.isEmpty()) return Map.of("type", "TOP_ITEMS", "found", false);
        String topQuality = warehouse.stream()
                .map(item -> (String) item.get("quality"))
                .max(Comparator.comparingInt(this::qualityOrder))
                .orElse("WHITE");
        List<Map<String, Object>> topItems = warehouse.stream()
                .filter(item -> topQuality.equals(item.get("quality")))
                .toList();
        String sampleName = (String) topItems.get(0).get("name");
        log.info("[Skill] 伊莎贝拉 顶级感知 quality={} count={}", topQuality, topItems.size());
        return Map.of(
                "type", "TOP_ITEMS",
                "quality", topQuality,
                "count", topItems.size(),
                "sampleName", sampleName
        );
    }

    /** 计算物品模糊价值等级（相对于仓库中其他物品的百分位） */
    private String calculateValueTier(Map<String, Object> item, List<Map<String, Object>> warehouse) {
        long itemValue = ((Number) item.get("value")).longValue();
        long belowCount = warehouse.stream()
                .map(w -> ((Number) w.get("value")).longValue())
                .filter(v -> v < itemValue)
                .count();
        double pct = (double) belowCount / warehouse.size();
        if (pct >= 0.8) return "极高";
        if (pct >= 0.6) return "高";
        if (pct >= 0.4) return "中";
        if (pct >= 0.2) return "低";
        return "极低";
    }

    private int qualityOrder(String q) {
        return switch (q) {
            case "WHITE"  -> 0;
            case "GREEN"  -> 1;
            case "BLUE"   -> 2;
            case "PURPLE" -> 3;
            case "GOLD"   -> 4;
            case "RED"    -> 5;
            default       -> -1;
        };
    }
}
