package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstCustody;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AstCustodyMapper extends BaseMapper<AstCustody> {

    /**
     * 某资产的全部领用历史（含已归还）。
     */
    @Select("SELECT * FROM ast_custody WHERE asset_id = #{assetId} AND deleted = 0 ORDER BY start_at DESC")
    List<AstCustody> findByAsset(@Param("assetId") Long assetId);

    /**
     * 某保管人当前在持资产（status='active'）。Tenant filter by interceptor.
     */
    @Select("SELECT * FROM ast_custody WHERE custodian_id = #{custodianId} "
        + "AND status = 'active' AND deleted = 0 ORDER BY start_at DESC")
    List<AstCustody> findActiveByCustodian(@Param("custodianId") Long custodianId);

    /**
     * 某资产的当前在持记录（用于唯一性校验）。
     */
    @Select("SELECT * FROM ast_custody WHERE asset_id = #{assetId} "
        + "AND status = 'active' AND deleted = 0 LIMIT 1")
    AstCustody findActiveByAsset(@Param("assetId") Long assetId);
}