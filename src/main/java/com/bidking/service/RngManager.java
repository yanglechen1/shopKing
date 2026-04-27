package com.bidking.service;

import com.bidking.entity.GameConfig;
import com.bidking.entity.ItemTemplate;
import com.bidking.mapper.ItemTemplateMapper;
import com.bidking.enums.Quality;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 仓库随机生成器
 * 根据 GameConfig 的品质权重随机生成一批物品，并为每件物品分配网格位置
 * 仓库网格：10列，物品从左到右、从上到下填充，中间无空位
 */
@Service
@RequiredArgsConstructor
public class RngManager {

    /** 仓库网格列数 */
    public static final int GRID_COLS = 10;

    private final ItemTemplateMapper itemTemplateMapper;
    private final ObjectMapper objectMapper;

    /**
     * 生成仓库物品列表（含网格位置）
     * @return 物品列表（含实际价值 + gridX/gridY/gridWidth/gridHeight）
     */
    @SneakyThrows
    public List<Map<String, Object>> generate(GameConfig config) {
        int size = ThreadLocalRandom.current()
                .nextInt(config.getWarehouseSizeMin(), config.getWarehouseSizeMax() + 1);

        Map<String, Integer> weights = objectMapper.readValue(
                config.getQualityWeights(), new TypeReference<>() {});

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String quality = weightedRandom(weights);
            ItemTemplate tpl = itemTemplateMapper.randomByQuality(quality);
            if (tpl == null) continue;
            items.add(buildItem(tpl));
        }

        // 黑匣子
        if (Boolean.TRUE.equals(config.getBlackBoxEnabled())
                && ThreadLocalRandom.current().nextInt(100) < 30) {
            ItemTemplate blackBox = itemTemplateMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ItemTemplate>()
                            .eq(ItemTemplate::getIsBlackBox, true));
            if (blackBox != null) {
                items.add(buildItem(blackBox));
            }
        }

        // 为所有物品分配网格位置
        assignGridPositions(items);

        return items;
    }

    /**
     * 为物品分配网格位置（10列，从左到右、从上到下填充，中间无空位）
     * 物品顺序已随机（生成时随机抽取），gridX/gridY基于尺寸放置
     */
    private void assignGridPositions(List<Map<String, Object>> items) {
        int x = 0, y = 0;
        int currentRowHeight = 0;

        for (Map<String, Object> item : items) {
            int gw = ((Number) item.get("gridWidth")).intValue();
            int gh = ((Number) item.get("gridHeight")).intValue();

            // 当前行放不下，换行
            if (x + gw > GRID_COLS) {
                x = 0;
                y += currentRowHeight;
                currentRowHeight = 0;
            }

            item.put("gridX", x);
            item.put("gridY", y);

            x += gw;
            if (gh > currentRowHeight) {
                currentRowHeight = gh;
            }
        }
    }

    /** 按权重随机选品质 */
    private String weightedRandom(Map<String, Integer> weights) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int rand = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (rand < cumulative) return entry.getKey();
        }
        return Quality.WHITE.name();
    }

    private Map<String, Object> buildItem(ItemTemplate tpl) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("templateId", tpl.getId());
        item.put("name", tpl.getName());
        item.put("category", tpl.getCategory());
        item.put("quality", tpl.getQuality());
        item.put("value", tpl.getValue());          // 固定价值，只存服务端，不下发客户端
        item.put("isBlackBox", tpl.getIsBlackBox());
        item.put("revealed", false);                // 开箱前不揭晓
        item.put("gridWidth", tpl.getGridWidth() != null ? tpl.getGridWidth() : 1);
        item.put("gridHeight", tpl.getGridHeight() != null ? tpl.getGridHeight() : 1);
        item.put("gridSize", tpl.getGridSize() != null ? tpl.getGridSize() : "1x1");
        // gridX/gridY 由 assignGridPositions 填充
        return item;
    }
}
