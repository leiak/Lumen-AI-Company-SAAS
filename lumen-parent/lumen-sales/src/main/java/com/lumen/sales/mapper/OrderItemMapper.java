package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT * FROM sal_order_item "
        + "WHERE order_id = #{orderId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id ASC")
    List<OrderItem> findByOrder(@Param("orderId") Long orderId,
                                @Param("tenantId") Long tenantId);
}