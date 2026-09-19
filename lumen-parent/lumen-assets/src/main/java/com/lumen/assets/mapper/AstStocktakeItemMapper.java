package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstStocktakeItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AstStocktakeItemMapper extends BaseMapper<AstStocktakeItem> {

    default List<AstStocktakeItem> findByStocktake(Long stocktakeId) {
        return selectList(new LambdaQueryWrapper<AstStocktakeItem>()
            .eq(AstStocktakeItem::getStocktakeId, stocktakeId)
            .orderByAsc(AstStocktakeItem::getId));
    }
}