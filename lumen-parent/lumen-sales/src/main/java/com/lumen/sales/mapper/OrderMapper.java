package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM sal_order "
        + "WHERE customer_id = #{customerId} AND status = #{status} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Order> findByCustomer(@Param("customerId") Long customerId,
                               @Param("status") String status,
                               @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_order "
        + "WHERE status = #{status} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<Order> findByStatus(@Param("status") String status,
                             @Param("tenantId") Long tenantId);
}