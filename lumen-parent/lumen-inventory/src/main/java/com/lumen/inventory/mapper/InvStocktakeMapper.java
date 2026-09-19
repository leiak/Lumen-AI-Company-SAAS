package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvStocktake;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvStocktakeMapper extends BaseMapper<InvStocktake> {

    @Select("SELECT * FROM inv_stocktake WHERE tenant_id = #{tenantId} "
        + "AND warehouse_id = #{warehouseId} AND period = #{period} AND deleted = 0 ORDER BY id DESC")
    List<InvStocktake> findByWarehouseAndPeriod(@Param("tenantId") Long tenantId,
                                                @Param("warehouseId") Long warehouseId,
                                                @Param("period") String period);

    @Select("SELECT * FROM inv_stocktake WHERE tenant_id = #{tenantId} "
        + "AND status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<InvStocktake> findByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);
}