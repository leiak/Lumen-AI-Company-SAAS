package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcSupplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcSupplierMapper extends BaseMapper<ProcSupplier> {

    /**
     * 按租户+code 查唯一。UNIQUE(tenant_id, code, deleted) 已保证唯一性。
     */
    @Select("SELECT * FROM proc_supplier WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 LIMIT 1")
    ProcSupplier findByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    /**
     * 按 status 查询。Tenant filter by interceptor.
     */
    @Select("SELECT * FROM proc_supplier WHERE tenant_id = #{tenantId} AND status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<ProcSupplier> findByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 计数: 活跃供应商数 (dashboard)。Literal "active" 与 service 常量对齐。
     */
    default long countActive(Long tenantId) {
        return selectCount(new LambdaQueryWrapper<ProcSupplier>()
            .eq(ProcSupplier::getTenantId, tenantId)
            .eq(ProcSupplier::getStatus, "active")).longValue();
    }
}