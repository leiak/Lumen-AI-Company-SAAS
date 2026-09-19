package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {

    @Select("SELECT * FROM sal_shipment "
        + "WHERE order_id = #{orderId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<Shipment> findByOrder(@Param("orderId") Long orderId,
                               @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_shipment "
        + "WHERE carrier = #{carrier} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<Shipment> findByCarrier(@Param("carrier") String carrier,
                                 @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_shipment "
        + "WHERE status = #{status} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<Shipment> findByStatus(@Param("status") String status,
                                @Param("tenantId") Long tenantId);
}