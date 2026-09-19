package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvSafetyStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvSafetyStockMapper extends BaseMapper<InvSafetyStock> {

    @Select("SELECT * FROM inv_safety_stock WHERE tenant_id = #{tenantId} "
        + "AND warehouse_id = #{warehouseId} AND deleted = 0 ORDER BY id")
    List<InvSafetyStock> findByWarehouse(@Param("tenantId") Long tenantId,
                                          @Param("warehouseId") Long warehouseId);

    @Select("SELECT * FROM inv_safety_stock WHERE tenant_id = #{tenantId} "
        + "AND alert_status = #{alertStatus} AND deleted = 0 ORDER BY id")
    List<InvSafetyStock> findByAlertStatus(@Param("tenantId") Long tenantId,
                                           @Param("alertStatus") String alertStatus);

    @Select("SELECT * FROM inv_safety_stock WHERE tenant_id = #{tenantId} "
        + "AND alert_status IN ('low','out_of_stock') AND deleted = 0 ORDER BY id")
    List<InvSafetyStock> findLowStock(@Param("tenantId") Long tenantId);
}