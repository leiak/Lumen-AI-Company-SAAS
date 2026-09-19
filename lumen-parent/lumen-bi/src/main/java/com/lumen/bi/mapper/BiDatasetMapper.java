package com.lumen.bi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.bi.entity.BiDataset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据集 mapper。UNIQUE(tenant_id, code, deleted)。
 */
@Mapper
public interface BiDatasetMapper extends BaseMapper<BiDataset> {

    default BiDataset findByCode(String code) {
        return selectOne(new LambdaQueryWrapper<BiDataset>()
            .eq(BiDataset::getCode, code)
            .eq(BiDataset::getDeleted, 0));
    }

    default BiDataset findByCodeAndTenant(String code, Long tenantId) {
        return selectOne(new LambdaQueryWrapper<BiDataset>()
            .eq(BiDataset::getCode, code)
            .eq(BiDataset::getTenantId, tenantId)
            .eq(BiDataset::getDeleted, 0));
    }

    @Select("SELECT * FROM bi_dataset WHERE source_type = #{sourceType} AND deleted = 0 ORDER BY id DESC")
    List<BiDataset> findBySourceType(@Param("sourceType") String sourceType);

    @Select("SELECT * FROM bi_dataset WHERE status = 'active' AND deleted = 0 ORDER BY id DESC")
    List<BiDataset> findActive();
}