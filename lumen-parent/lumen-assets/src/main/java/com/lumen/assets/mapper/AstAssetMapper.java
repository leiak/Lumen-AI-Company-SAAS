package com.lumen.assets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.assets.entity.AstAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AstAssetMapper extends BaseMapper<AstAsset> {

    /**
     * 按部门+状态查询资产列表。Tenant filter by interceptor.
     */
    @Select("SELECT * FROM ast_asset WHERE dept_id = #{deptId} AND status = #{status} AND deleted = 0")
    List<AstAsset> findByDeptAndStatus(@Param("deptId") Long deptId, @Param("status") String status);

    /**
     * 按分类查询资产列表。
     */
    @Select("SELECT * FROM ast_asset WHERE category_id = #{categoryId} AND deleted = 0 ORDER BY id DESC")
    List<AstAsset> findByCategory(@Param("categoryId") Long categoryId);

    /**
     * 按 status in (...) 查询（用于折旧批量按 in_use 过滤）。
     */
    default List<AstAsset> findByStatuses(List<String> statuses) {
        return selectList(new LambdaQueryWrapper<AstAsset>().in(AstAsset::getStatus, statuses));
    }
}