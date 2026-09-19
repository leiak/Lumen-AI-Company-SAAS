package com.lumen.message.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.message.entity.MsgTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MsgTemplateMapper extends BaseMapper<MsgTemplate> {

    /**
     * Find template by (code, channelCode, tenant). Bypasses tenant interceptor
     * because service-layer already pins tenant.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM msg_template WHERE code = #{code} "
        + "AND channel_code = #{channelCode} AND tenant_id = #{tenantId} "
        + "AND deleted = 0 LIMIT 1")
    MsgTemplate findByCodeAndChannel(@Param("code") String code,
                                     @Param("channelCode") String channelCode,
                                     @Param("tenantId") Long tenantId);

    default LambdaQueryWrapper<MsgTemplate> tenantWrapper() {
        return new LambdaQueryWrapper<>();
    }
}