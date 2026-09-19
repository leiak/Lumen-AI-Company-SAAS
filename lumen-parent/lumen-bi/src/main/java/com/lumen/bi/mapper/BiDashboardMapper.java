package com.lumen.bi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.bi.entity.BiDashboard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 看板 mapper。UNIQUE(tenant_id, code, deleted)。
 */
@Mapper
public interface BiDashboardMapper extends BaseMapper<BiDashboard> {

    default BiDashboard findByCode(String code) {
        return selectOne(new LambdaQueryWrapper<BiDashboard>()
            .eq(BiDashboard::getCode, code)
            .eq(BiDashboard::getDeleted, 0));
    }

    default BiDashboard findByCodeAndTenant(String code, Long tenantId) {
        return selectOne(new LambdaQueryWrapper<BiDashboard>()
            .eq(BiDashboard::getCode, code)
            .eq(BiDashboard::getTenantId, tenantId)
            .eq(BiDashboard::getDeleted, 0));
    }

    @Select("SELECT * FROM bi_dashboard WHERE status = 'published' AND deleted = 0 ORDER BY id DESC")
    List<BiDashboard> findPublished();

    @Select("SELECT * FROM bi_dashboard WHERE status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<BiDashboard> findByStatus(@Param("status") String status);
}