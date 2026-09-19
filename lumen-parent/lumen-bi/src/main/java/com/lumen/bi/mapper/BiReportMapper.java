package com.lumen.bi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.bi.entity.BiReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报表 mapper。UNIQUE(tenant_id, code, deleted)。
 */
@Mapper
public interface BiReportMapper extends BaseMapper<BiReport> {

    default BiReport findByCode(String code) {
        return selectOne(new LambdaQueryWrapper<BiReport>()
            .eq(BiReport::getCode, code)
            .eq(BiReport::getDeleted, 0));
    }

    default BiReport findByCodeAndTenant(String code, Long tenantId) {
        return selectOne(new LambdaQueryWrapper<BiReport>()
            .eq(BiReport::getCode, code)
            .eq(BiReport::getTenantId, tenantId)
            .eq(BiReport::getDeleted, 0));
    }

    @Select("SELECT * FROM bi_report WHERE schedule = #{schedule} AND deleted = 0 ORDER BY id DESC")
    List<BiReport> findBySchedule(@Param("schedule") String schedule);

    @Select("SELECT * FROM bi_report WHERE status = 'active' AND deleted = 0 ORDER BY id DESC")
    List<BiReport> findActive();
}