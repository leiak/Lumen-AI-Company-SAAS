package com.lumen.bi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.bi.entity.BiMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 指标 mapper。UNIQUE(tenant_id, code, deleted) 保证 code 租户内唯一。
 */
@Mapper
public interface BiMetricMapper extends BaseMapper<BiMetric> {

    /**
     * 按 code 查唯一 (跨租户过滤交给 service 层)。
     */
    default BiMetric findByCode(String code) {
        return selectOne(new LambdaQueryWrapper<BiMetric>()
            .eq(BiMetric::getCode, code)
            .eq(BiMetric::getDeleted, 0));
    }

    /**
     * 按 code + tenant 查唯一 (service 层 tenant 校验)。
     */
    default BiMetric findByCodeAndTenant(String code, Long tenantId) {
        return selectOne(new LambdaQueryWrapper<BiMetric>()
            .eq(BiMetric::getCode, code)
            .eq(BiMetric::getTenantId, tenantId)
            .eq(BiMetric::getDeleted, 0));
    }

    /**
     * 按 category 列出 (service 层 tenant 过滤)。
     */
    @Select("SELECT * FROM bi_metric WHERE category = #{category} AND deleted = 0 ORDER BY id DESC")
    List<BiMetric> findByCategory(@Param("category") String category);

    /**
     * 列出所有 active 指标。
     */
    @Select("SELECT * FROM bi_metric WHERE status = 'active' AND deleted = 0 ORDER BY id DESC")
    List<BiMetric> findActive();
}