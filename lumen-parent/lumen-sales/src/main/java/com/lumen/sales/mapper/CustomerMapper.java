package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    @Select("SELECT * FROM sal_customer "
        + "WHERE owner_user_id = #{ownerUserId} AND status = #{status} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Customer> findByOwnerAndStatus(@Param("ownerUserId") Long ownerUserId,
                                        @Param("status") String status,
                                        @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_customer "
        + "WHERE status = 'in_pool' AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC LIMIT #{limit}")
    List<Customer> findInPool(@Param("tenantId") Long tenantId,
                              @Param("limit") int limit);
}