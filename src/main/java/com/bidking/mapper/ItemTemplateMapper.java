package com.bidking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidking.entity.ItemTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ItemTemplateMapper extends BaseMapper<ItemTemplate> {
    @Select("SELECT * FROM item_template WHERE quality = #{quality} AND is_black_box = 0 ORDER BY RAND() LIMIT 1")
    ItemTemplate randomByQuality(@Param("quality") String quality);

    @Select("SELECT * FROM item_template WHERE quality = #{quality} AND category = #{category} AND is_black_box = 0 ORDER BY RAND() LIMIT 1")
    ItemTemplate randomByQualityAndCategory(@Param("quality") String quality, @Param("category") String category);

    /** 一次查出所有非黑匣子物品模板，内存中做随机抽取 */
    @Select("SELECT * FROM item_template WHERE is_black_box = 0")
    List<ItemTemplate> selectAllNonBlackBox();
}
