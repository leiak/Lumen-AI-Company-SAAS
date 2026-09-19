package com.lumen.workflow.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.workflow.entity.WfTaskHistory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * History rows inherit tenant scoping from their parent instance; the
 * {@code wf_task_history} table itself carries no tenant_id column.
 */
@InterceptorIgnore(tenantLine = "true")
@Mapper
public interface WfTaskHistoryMapper extends BaseMapper<WfTaskHistory> {

    default List<WfTaskHistory> listByInstanceId(Long instanceId) {
        return selectList(new LambdaQueryWrapper<WfTaskHistory>()
            .eq(WfTaskHistory::getInstanceId, instanceId)
            .orderByDesc(WfTaskHistory::getOperatedTime));
    }
}