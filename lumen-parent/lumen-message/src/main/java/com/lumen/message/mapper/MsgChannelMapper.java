package com.lumen.message.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.message.entity.MsgChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MsgChannelMapper extends BaseMapper<MsgChannel> {

    /**
     * Find a channel by code + tenant. Bypasses the tenant interceptor because
     * service-layer code already filters by tenant; this is for admin lookup
     * where the caller supplies the tenant explicitly.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM msg_channel WHERE code = #{code} AND tenant_id = #{tenantId} "
        + "AND deleted = 0 LIMIT 1")
    MsgChannel findByCodeAndTenant(@Param("code") String code,
                                   @Param("tenantId") Long tenantId);

    default LambdaQueryWrapper<MsgChannel> tenantWrapper() {
        return new LambdaQueryWrapper<>();
    }
}