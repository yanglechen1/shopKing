package com.bidking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 物品字典表
 * 存储所有可出现在仓库中的物品模板
 * RngManager 根据品质权重从此表随机抽取
 */
@Data
@TableName("item_template")
public class ItemTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物品名称（中文） */
    private String name;

    /** 品类：FURNITURE/DIGITAL/ANTIQUE */
    private String category;

    /** 品质等级：COMMON/ADVANCED/RARE/EPIC/LEGEND/MYTH */
    private String quality;

    /** 实际价值（固定值） */
    private Long value;

    /** 仓库网格占用宽度（格数） */
    private Integer gridWidth = 1;

    /** 仓库网格占用高度（格数） */
    private Integer gridHeight = 1;

    /** 网格占用尺寸 WxH 格式（如 2x2、1x1），用于前端显示 */
    private String gridSize = "1x1";

    /** 是否为黑匣子（开启后随机，可能是巨额宝物也可能是废铁） */
    private Boolean isBlackBox = false;

    private String description;
    private String imageUrl;
}
