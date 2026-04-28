package com.bidking.service;

import com.bidking.dto.WarehouseRegion;
import com.bidking.dto.WarehouseTheme;
import com.bidking.entity.GameConfig;
import com.bidking.entity.ItemTemplate;
import com.bidking.mapper.ItemTemplateMapper;
import com.bidking.mapper.RegionPresetMapper;
import com.bidking.mapper.ThemePresetMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 仓库随机生成器
 * 根据 GameConfig 的品质权重随机生成一批物品，并为每件物品分配网格位置
 * 仓库网格：10列，物品紧凑填充，不留空隙（除末尾）
 *
 * 支持 Theme × Region 双维配置：
 *   Region 决定仓库大小 + 品质分布
 *   Theme  决定物品种类偏向
 *
 * 性能：一次 DB 查询加载全部模板，内存分组后随机抽取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RngManager {

    /** 仓库网格列数 */
    public static final int GRID_COLS = 10;

    private final ItemTemplateMapper itemTemplateMapper;
    private final RegionPresetMapper regionPresetMapper;
    private final ThemePresetMapper themePresetMapper;
    private final ObjectMapper objectMapper;

    /**
     * 生成仓库物品列表（含网格位置）
     * 优先使用 warehouseRegions/warehouseThemes（Theme × Region 模式），
     * 未配置时回退旧的 warehouseSizeMin/Max + qualityWeights。
     */
    @SneakyThrows
    public List<Map<String, Object>> generate(GameConfig config) {
        // 一次查出全部模板，按 quality → category → List 分组
        List<ItemTemplate> allTemplates = itemTemplateMapper.selectAllNonBlackBox();
        Map<String, Map<String, List<ItemTemplate>>> grouped = allTemplates.stream()
                .collect(Collectors.groupingBy(ItemTemplate::getQuality,
                         Collectors.groupingBy(ItemTemplate::getCategory)));

        // 按 quality 汇总（用于 fallback）
        Map<String, List<ItemTemplate>> byQuality = allTemplates.stream()
                .collect(Collectors.groupingBy(ItemTemplate::getQuality));

        List<WarehouseRegion> regions = parseRegions(config);
        List<WarehouseTheme> themes = parseThemes(config);

        Map<String, Integer> qualityWeights;
        Map<String, Integer> categoryWeights = null;
        int itemCount;

        if (regions != null && !regions.isEmpty() && themes != null && !themes.isEmpty()) {
            WarehouseTheme theme = resolveTheme(config.getWarehouseTheme(), themes);
            WarehouseRegion region = resolveRegion(config.getWarehouseRegion(), regions);

            log.info("[Rng] 仓库生成 theme={}({}) region={}({})",
                    theme.getKey(), theme.getName(), region.getKey(), region.getName());

            qualityWeights = region.getQualityWeights();
            categoryWeights = theme.getCategoryWeights();
            itemCount = ThreadLocalRandom.current()
                    .nextInt(region.getItemCountMin(), region.getItemCountMax() + 1);
        } else {
            log.info("[Rng] 仓库生成（旧模式） sizeMin={} sizeMax={}",
                    config.getWarehouseSizeMin(), config.getWarehouseSizeMax());

            qualityWeights = objectMapper.readValue(
                    config.getQualityWeights(), new TypeReference<>() {});
            itemCount = ThreadLocalRandom.current()
                    .nextInt(config.getWarehouseSizeMin(), config.getWarehouseSizeMax() + 1);
        }

        // 按品质权重+随机偏移预分配各品质数量
        int deviation = config.getWeightDeviation() != null ? config.getWeightDeviation() : 0;
        Map<String, Integer> qualityCounts = resolveQualityCounts(qualityWeights, itemCount, deviation);

        // 按分配的数量逐品质生成物品
        List<Map<String, Object>> items = new ArrayList<>();
        for (var qc : qualityCounts.entrySet()) {
            String quality = qc.getKey();
            int count = qc.getValue();
            for (int i = 0; i < count; i++) {
                ItemTemplate tpl;
                if (categoryWeights != null) {
                    String category = weightedRandom(categoryWeights);
                    tpl = pickRandom(grouped, quality, category);
                    if (tpl == null) {
                        log.debug("[Rng] quality={} category={} 无匹配，回退到仅品质查询", quality, category);
                        tpl = pickRandom(byQuality, quality);
                    }
                } else {
                    tpl = pickRandom(byQuality, quality);
                }
                if (tpl != null) {
                    items.add(buildItem(tpl));
                } else {
                    log.warn("[Rng] quality={} 未找到匹配物品模板", quality);
                }
            }
        }

        // 打乱顺序，避免同品质堆积
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = items.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Collections.swap(items, i, j);
        }

        finishItems(items, config);
        return items;
    }

    /** 从分组 Map 中随机取一个元素 */
    private ItemTemplate pickRandom(Map<String, Map<String, List<ItemTemplate>>> grouped,
                                    String quality, String category) {
        Map<String, List<ItemTemplate>> byCategory = grouped.get(quality);
        if (byCategory == null) return null;
        List<ItemTemplate> list = byCategory.get(category);
        if (list == null || list.isEmpty()) return null;
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** 从按 quality 分组的列表中随机取一个 */
    private ItemTemplate pickRandom(Map<String, List<ItemTemplate>> byQuality, String quality) {
        List<ItemTemplate> list = byQuality.get(quality);
        if (list == null || list.isEmpty()) return null;
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** 黑匣子 + 网格分配 */
    private void finishItems(List<Map<String, Object>> items, GameConfig config) {
        if (items.isEmpty()) {
            log.error("[Rng] 仓库生成为空！请检查 item_template 表是否已导入 data.sql 数据");
        }

        if (Boolean.TRUE.equals(config.getBlackBoxEnabled())
                && ThreadLocalRandom.current().nextInt(100) < 30) {
            ItemTemplate blackBox = itemTemplateMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ItemTemplate>()
                            .eq(ItemTemplate::getIsBlackBox, true));
            if (blackBox != null) {
                items.add(buildItem(blackBox));
            }
        }

        assignGridPositions(items);
    }

    /** 从 region_preset 表加载所有地区预设 */
    private List<WarehouseRegion> parseRegions(GameConfig config) {
        var list = regionPresetMapper.selectList(null);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list.stream().map(p -> {
            WarehouseRegion r = new WarehouseRegion();
            r.setKey(p.getRegionKey());
            r.setName(p.getName());
            r.setItemCountMin(p.getItemCountMin());
            r.setItemCountMax(p.getItemCountMax());
            r.setWeight(p.getWeight() != null ? p.getWeight() : 10);
            try {
                r.setQualityWeights(objectMapper.readValue(p.getQualityWeights(), new TypeReference<>() {}));
            } catch (Exception e) {
                log.warn("[Rng] 解析地区品质权重失败 region={}", p.getRegionKey());
                r.setQualityWeights(Map.of());
            }
            return r;
        }).collect(Collectors.toList());
    }

    /** 从 theme_preset 表加载所有主题预设 */
    private List<WarehouseTheme> parseThemes(GameConfig config) {
        var list = themePresetMapper.selectList(null);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list.stream().map(p -> {
            WarehouseTheme t = new WarehouseTheme();
            t.setKey(p.getThemeKey());
            t.setName(p.getName());
            try {
                t.setCategoryWeights(objectMapper.readValue(p.getCategoryWeights(), new TypeReference<>() {}));
            } catch (Exception e) {
                log.warn("[Rng] 解析主题品类权重失败 theme={}", p.getThemeKey());
                t.setCategoryWeights(Map.of());
            }
            return t;
        }).collect(Collectors.toList());
    }

    private WarehouseRegion resolveRegion(String regionKey, List<WarehouseRegion> regions) {
        if ("RANDOM".equalsIgnoreCase(regionKey)) {
            return regions.get(ThreadLocalRandom.current().nextInt(regions.size()));
        }
        return regions.stream()
                .filter(r -> r.getKey().equalsIgnoreCase(regionKey))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("[Rng] 未找到 region={}，使用第一个", regionKey);
                    return regions.get(0);
                });
    }

    private WarehouseTheme resolveTheme(String themeKey, List<WarehouseTheme> themes) {
        if ("RANDOM".equalsIgnoreCase(themeKey)) {
            return themes.get(ThreadLocalRandom.current().nextInt(themes.size()));
        }
        return themes.stream()
                .filter(t -> t.getKey().equalsIgnoreCase(themeKey))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("[Rng] 未找到 theme={}，使用第一个", themeKey);
                    return themes.get(0);
                });
    }

    /**
     * 二维占用网格紧凑放置，不留空隙（除末尾）
     * 从上到下、从左到右扫描，找到第一个能容纳物品的位置
     */
    private void assignGridPositions(List<Map<String, Object>> items) {
        List<boolean[]> grid = new ArrayList<>(); // 动态增长的行列表

        for (Map<String, Object> item : items) {
            int gw = ((Number) item.get("gridWidth")).intValue();
            int gh = ((Number) item.get("gridHeight")).intValue();

            int[] pos = findPosition(grid, gw, gh);

            item.put("gridX", pos[0]);
            item.put("gridY", pos[1]);

            markOccupied(grid, pos[0], pos[1], gw, gh);
        }
    }

    /** 扫描寻找第一个能放下的位置 */
    private int[] findPosition(List<boolean[]> grid, int gw, int gh) {
        int y = 0;
        while (true) {
            for (int x = 0; x <= GRID_COLS - gw; x++) {
                if (canPlace(grid, x, y, gw, gh)) {
                    return new int[]{x, y};
                }
            }
            y++;
        }
    }

    /** 检查 (x,y) 处能否放下 gw×gh 的物品 */
    private boolean canPlace(List<boolean[]> grid, int x, int y, int gw, int gh) {
        for (int dy = 0; dy < gh; dy++) {
            ensureRow(grid, y + dy);
            boolean[] row = grid.get(y + dy);
            for (int dx = 0; dx < gw; dx++) {
                if (row[x + dx]) return false;
            }
        }
        return true;
    }

    /** 标记 gw×gh 区域为已占用 */
    private void markOccupied(List<boolean[]> grid, int x, int y, int gw, int gh) {
        for (int dy = 0; dy < gh; dy++) {
            ensureRow(grid, y + dy);
            boolean[] row = grid.get(y + dy);
            for (int dx = 0; dx < gw; dx++) {
                row[x + dx] = true;
            }
        }
    }

    /** 确保 grid 有第 rowIndex 行 */
    private void ensureRow(List<boolean[]> grid, int rowIndex) {
        while (rowIndex >= grid.size()) {
            grid.add(new boolean[GRID_COLS]);
        }
    }

    /** 按权重随机选 */
    private String weightedRandom(Map<String, Integer> weights) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int rand = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (rand < cumulative) return entry.getKey();
        }
        return weights.keySet().iterator().next();
    }

    /**
     * 按品质权重 + 随机偏移计算各品质应生成的数量。
     * 偏差使每局各品质数量在理论值附近波动，增加随机性。
     */
    static Map<String, Integer> resolveQualityCounts(Map<String, Integer> weights, int totalItems, int deviationPercent) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        double deviation = deviationPercent / 100.0;

        // 各品质理论数量 + 随机偏移
        Map<String, Double> raw = new LinkedHashMap<>();
        double rawSum = 0;
        for (var entry : weights.entrySet()) {
            double theoretical = (double) entry.getValue() / totalWeight * totalItems;
            double offset = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * deviation * totalItems;
            double v = Math.max(0, theoretical + offset);
            raw.put(entry.getKey(), v);
            rawSum += v;
        }

        if (rawSum <= 0) {
            // 极端情况：所有品质均被偏移到 0，回退到均匀分配
            Map<String, Integer> fallback = new LinkedHashMap<>();
            int each = totalItems / weights.size();
            int extra = totalItems % weights.size();
            int i = 0;
            for (String q : weights.keySet()) {
                fallback.put(q, each + (i < extra ? 1 : 0));
                i++;
            }
            return fallback;
        }

        // 归一化到 totalItems
        double scale = totalItems / rawSum;
        double[] remainders = new double[weights.size()];
        int[] counts = new int[weights.size()];
        int idx = 0;
        int assigned = 0;
        for (String quality : weights.keySet()) {
            double scaled = raw.get(quality) * scale;
            counts[idx] = (int) scaled;
            remainders[idx] = scaled - counts[idx];
            assigned += counts[idx];
            idx++;
        }

        // 将余数分配给小数部分最大的品质
        int remaining = totalItems - assigned;
        while (remaining > 0) {
            int best = 0;
            for (int i = 1; i < remainders.length; i++) {
                if (remainders[i] > remainders[best]) best = i;
            }
            counts[best]++;
            remainders[best] = -1;
            remaining--;
        }

        // 组装结果
        Map<String, Integer> result = new LinkedHashMap<>();
        idx = 0;
        for (String quality : weights.keySet()) {
            result.put(quality, Math.max(0, Math.min(totalItems, counts[idx])));
            idx++;
        }
        return result;
    }

    private Map<String, Object> buildItem(ItemTemplate tpl) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("templateId", tpl.getId());
        item.put("name", tpl.getName());
        item.put("category", tpl.getCategory());
        item.put("quality", tpl.getQuality());
        item.put("value", tpl.getValue());
        item.put("isBlackBox", tpl.getIsBlackBox());
        item.put("revealed", false);
        item.put("gridWidth", tpl.getGridWidth() != null ? tpl.getGridWidth() : 1);
        item.put("gridHeight", tpl.getGridHeight() != null ? tpl.getGridHeight() : 1);
        item.put("gridSize", tpl.getGridSize() != null ? tpl.getGridSize() : "1x1");
        return item;
    }
}
