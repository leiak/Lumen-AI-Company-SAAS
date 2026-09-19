package com.lumen.workflow.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Tasks reference the user but the {@code wf_task} table has no tenant_id column.
 * Tenant scoping is done via the instance's tenant_id at the service layer.
 */
@InterceptorIgnore(tenantLine = "true")
@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {

    /**
     * 查找当前用户的待办：assignee == userId，或 candidate_users 包含 userId。
     * candidate_roles 的匹配由 Service 层在内存中完成（因为角色可能来自用户上下文，不在 wf_task 表里）。
     *
     * TODO: 使用 MySQL JSON_CONTAINS / JSON_OVERLAPS 完整支持 candidate_roles 过滤。
     */
    @Select("SELECT * FROM wf_task WHERE status = 0 AND deleted = 0 " +
        "AND (assignee = #{assignee} OR JSON_CONTAINS(candidate_users, CAST(#{assignee} AS JSON))) " +
        "ORDER BY create_time DESC")
    List<WfTask> selectTodoByUser(@Param("assignee") Long assignee);
}