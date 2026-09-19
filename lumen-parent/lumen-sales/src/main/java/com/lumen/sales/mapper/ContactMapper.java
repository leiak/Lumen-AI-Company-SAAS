package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Contact;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContactMapper extends BaseMapper<Contact> {

    @Select("SELECT * FROM sal_contact "
        + "WHERE customer_id = #{customerId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY is_primary DESC, id ASC")
    List<Contact> findByCustomer(@Param("customerId") Long customerId,
                                 @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_contact "
        + "WHERE customer_id = #{customerId} AND is_primary = 1 "
        + "AND tenant_id = #{tenantId} AND deleted = 0 LIMIT 1")
    Contact findPrimary(@Param("customerId") Long customerId,
                        @Param("tenantId") Long tenantId);
}