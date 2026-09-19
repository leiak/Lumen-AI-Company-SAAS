package com.lumen.bi.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.bi.entity.BiPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 mapper。记录 BI 资源 (dashboard/metric/report/dataset) 对
 * 用户/角色/部门 的 view/edit/admin 授权。
 */
@Mapper
public interface BiPermissionMapper extends BaseMapper<BiPermission> {

    /**
     * 安全要求 #6: 查某资源的所有授权。
     */
    @Select("SELECT * FROM bi_permission WHERE resource_type = #{resourceType} " +
            "AND resource_id = #{resourceId} AND deleted = 0 ORDER BY id")
    List<BiPermission> findByResource(@Param("resourceType") String resourceType,
                                      @Param("resourceId") Long resourceId);

    /**
     * 查某 principal (用户/角色/部门) 所有授权。
     */
    @Select("SELECT * FROM bi_permission WHERE principal_type = #{principalType} " +
            "AND principal_id = #{principalId} AND deleted = 0 ORDER BY id")
    List<BiPermission> findByPrincipal(@Param("principalType") String principalType,
                                       @Param("principalId") Long principalId);

    /**
     * 列出某 principal 可访问的所有资源 id (跨资源类型统一查询)。
     * controller 层根据 resourceType 二次过滤。
     */
    @Select("SELECT DISTINCT resource_id FROM bi_permission " +
            "WHERE principal_type = #{principalType} AND principal_id = #{principalId} " +
            "AND resource_type = #{resourceType} AND deleted = 0")
    List<Long> findAccessibleResources(@Param("resourceType") String resourceType,
                                        @Param("principalType") String principalType,
                                        @Param("principalId") Long principalId);

    /**
     * 查指定资源 + principal 是否已有授权 (用于 grant 时去重)。
     */
    default BiPermission findExisting(String resourceType, Long resourceId,
                                      String principalType, Long principalId) {
        return selectOne(new LambdaQueryWrapper<BiPermission>()
            .eq(BiPermission::getResourceType, resourceType)
            .eq(BiPermission::getResourceId, resourceId)
            .eq(BiPermission::getPrincipalType, principalType)
            .eq(BiPermission::getPrincipalId, principalId)
            .eq(BiPermission::getDeleted, 0));
    }
}