package com.bidking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidking.entity.ItemTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ItemTemplateMapper extends BaseMapper<ItemTemplate> {
    @Select("SELECT * FROM item_template WHERE quality = #{quality} AND is_black_box = 0 ORDER BY RAND() LIMIT 1")
    ItemTemplate randomByQuality(String quality);
}
