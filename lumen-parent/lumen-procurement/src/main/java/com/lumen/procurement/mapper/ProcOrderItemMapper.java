package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcOrderItemMapper extends BaseMapper<ProcOrderItem> {

    /**
     * 按 order_id 查询所有明细。
     */
    @Select("SELECT * FROM proc_order_item WHERE tenant_id = #{tenantId} "
        + "AND order_id = #{orderId} AND deleted = 0 ORDER BY id ASC")
    List<ProcOrderItem> findByOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
}