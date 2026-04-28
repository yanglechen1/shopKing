package com.bidking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库地区预设 —— 决定仓库大小 + 品质分布
 * 存储在 region_preset 表，可热更新
 */
@Data
@TableName("region_preset")
public class RegionPreset {
    @TableId
    private Integer id;
    private String regionKey;
    private String name;
    private Integer itemCountMin;
    private Integer itemCountMax;
    private Integer weight;
    private String qualityWeights;  // JSON: {"WHITE":45,"GREEN":25,...}
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
