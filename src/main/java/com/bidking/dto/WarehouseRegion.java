package com.bidking.dto;

import lombok.Data;
import java.util.Map;

/**
 * 仓库地区预设 —— 决定仓库大小 + 品质分布
 * JSON 反序列化用，存储在 game_config.warehouse_regions
 */
@Data
public class WarehouseRegion {
    private String key;
    private String name;
    private int itemCountMin;
    private int itemCountMax;
    private int weight;
    private Map<String, Integer> qualityWeights;
}
