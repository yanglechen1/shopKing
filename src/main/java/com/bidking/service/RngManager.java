package com.bidking.service;

import com.bidking.dto.WarehouseRegion;
import com.bidking.dto.WarehouseTheme;
import com.bidking.entity.GameConfig;
import com.bidking.entity.ItemTemplate;
import com.bidking.mapper.ItemTemplateMapper;
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

        // 内存中随机抽取物品
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            String quality = weightedRandom(qualityWeights);
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

            if (tpl == null) {
                log.warn("[Rng] quality={} 未找到匹配物品模板", quality);
                continue;
            }
            items.add(buildItem(tpl));
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

    /** 解析 Region 预设 JSON */
    @SneakyThrows
    private List<WarehouseRegion> parseRegions(GameConfig config) {
        if (config.getWarehouseRegions() == null || config.getWarehouseRegions().isBlank()) {
            return Collections.emptyList();
        }
        return objectMapper.readValue(config.getWarehouseRegions(),
                new TypeReference<List<WarehouseRegion>>() {});
    }

    /** 解析 Theme 预设 JSON */
    @SneakyThrows
    private List<WarehouseTheme> parseThemes(GameConfig config) {
        if (config.getWarehouseThemes() == null || config.getWarehouseThemes().isBlank()) {
            return Collections.emptyList();
        }
        return objectMapper.readValue(config.getWarehouseThemes(),
                new TypeReference<List<WarehouseTheme>>() {});
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
