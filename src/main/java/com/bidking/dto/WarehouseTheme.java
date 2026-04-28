package com.bidking.dto;

import lombok.Data;
import java.util.Map;

/**
 * 仓库主题预设 —— 决定品类分布
 * JSON 反序列化用，存储在 game_config.warehouse_themes
 */
@Data
public class WarehouseTheme {
    private String key;
    private String name;
    private Map<String, Integer> categoryWeights;
}
