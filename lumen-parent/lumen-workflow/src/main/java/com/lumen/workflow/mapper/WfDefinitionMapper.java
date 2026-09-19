package com.lumen.workflow.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.workflow.entity.WfDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Process definitions are catalog-level and intentionally cross-tenant.
 * The {@code wf_definition} table has no {@code tenant_id} column, so all
 * BaseMapper methods on this interface must skip the TenantLineInnerInterceptor.
 */
@InterceptorIgnore(tenantLine = "true")
@Mapper
public interface WfDefinitionMapper extends BaseMapper<WfDefinition> {

    /**
     * 返回指定 defKey 的最新已发布 (status=1) 定义。
     * 调用方负责校验 status==1。
     */
    @Select("SELECT * FROM wf_definition WHERE def_key = #{defKey} AND deleted = 0 ORDER BY version DESC LIMIT 1")
    WfDefinition selectLatestByKey(@Param("defKey") String defKey);
}