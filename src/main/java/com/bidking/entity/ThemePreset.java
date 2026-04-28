package com.bidking.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库主题预设 —— 决定品类分布
 * 存储在 theme_preset 表，可热更新
 */
@Data
@TableName("theme_preset")
public class ThemePreset {
    @TableId
    private Integer id;
    private String themeKey;
    private String name;
    private String categoryWeights;  // JSON: {"FURNITURE":40,"DIGITAL":7,...}
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
