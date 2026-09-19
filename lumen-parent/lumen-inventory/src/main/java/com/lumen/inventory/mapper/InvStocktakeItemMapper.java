package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvStocktakeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvStocktakeItemMapper extends BaseMapper<InvStocktakeItem> {

    @Select("SELECT * FROM inv_stocktake_item WHERE tenant_id = #{tenantId} "
        + "AND stocktake_id = #{stocktakeId} AND deleted = 0 ORDER BY id")
    List<InvStocktakeItem> findByStocktake(@Param("tenantId") Long tenantId,
                                           @Param("stocktakeId") Long stocktakeId);
}