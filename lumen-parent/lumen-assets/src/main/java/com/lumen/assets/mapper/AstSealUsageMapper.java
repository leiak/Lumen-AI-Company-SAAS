package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstSealUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AstSealUsageMapper extends BaseMapper<AstSealUsage> {

    /**
     * 某印章的用印历史。
     */
    @Select("SELECT * FROM ast_seal_usage WHERE seal_id = #{sealId} AND deleted = 0 ORDER BY used_at DESC")
    List<AstSealUsage> findBySeal(@Param("sealId") Long sealId);
}