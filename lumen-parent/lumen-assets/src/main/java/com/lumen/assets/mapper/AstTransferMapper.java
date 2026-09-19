package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AstTransferMapper extends BaseMapper<AstTransfer> {

    /**
     * 某资产的调拨历史。
     */
    @Select("SELECT * FROM ast_transfer WHERE asset_id = #{assetId} AND deleted = 0 ORDER BY transfer_date DESC")
    List<AstTransfer> findByAsset(@Param("assetId") Long assetId);
}