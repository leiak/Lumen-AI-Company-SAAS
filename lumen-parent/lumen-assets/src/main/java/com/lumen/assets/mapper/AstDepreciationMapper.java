package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstDepreciation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AstDepreciationMapper extends BaseMapper<AstDepreciation> {

    /**
     * 某资产某期间是否已计提。
     */
    @Select("SELECT * FROM ast_depreciation WHERE asset_id = #{assetId} "
        + "AND period = #{period} AND deleted = 0 LIMIT 1")
    AstDepreciation findByAssetAndPeriod(@Param("assetId") Long assetId, @Param("period") String period);
}