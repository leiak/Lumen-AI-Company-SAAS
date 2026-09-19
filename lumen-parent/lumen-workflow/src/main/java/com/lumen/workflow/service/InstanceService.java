package com.lumen.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.workflow.entity.WfInstance;
import com.lumen.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final WfInstanceMapper instanceMapper;
    private final EngineService engineService;

    /** Delegates to {@link EngineService#startInstance}. */
    public Long start(String defKey, String businessKey, Map<String, Object> variables) {
        return engineService.startInstance(defKey, businessKey, variables);
    }

    /**
     * Fetch an instance by id, enforcing tenant scoping.
     * Returns 404 (not 403) on tenant mismatch to avoid existence disclosure.
     */
    public WfInstance get(Long id) {
        WfInstance i = instanceMapper.selectById(id);
        if (i == null) throw new ServiceException(404, "Instance not found: " + id);
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        Set<String> roles = ctx.getRoles();
        boolean isSuperAdmin = roles != null && roles.contains(EngineService.SUPER_ADMIN_ROLE);
        if (!isSuperAdmin) {
            Long ctxTenant = ctx.getTenantId();
            if (ctxTenant == null || !ctxTenant.equals(i.getTenantId())) {
                throw new ServiceException(404, "Instance not found: " + id);
            }
        }
        return i;
    }

    /**
     * Page instances belonging to the current user as starter. The TenantLineInnerInterceptor
     * already injects the tenant_id WHERE clause, so this is naturally tenant-scoped.
     */
    public IPage<WfInstance> pageByCurrentUser(int pageNum, int pageSize) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new ServiceException(401, "No user context");
        }
        return instanceMapper.selectPage(
            Page.of(pageNum, pageSize),
            new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getStarter, userId)
                .orderByDesc(WfInstance::getId));
    }

    @Transactional
    public WfInstance cancel(Long id, String reason) {
        WfInstance instance = get(id);
        // get() already enforces tenant scoping (and 404 on mismatch).
        if (instance.getStatus() != EngineService.INSTANCE_STATUS_RUNNING) {
            throw new ServiceException(409,
                "Only running instances can be cancelled; current status=" + instance.getStatus());
        }
        // Authorization: only the starter (or super_admin) may cancel.
        UserContext ctx = engineService.requireUserContext();
        engineService.assertCanCancel(instance, ctx);
        engineService.closeInstanceCancelled(instance, reason);
        log.info("Cancelled instance id={} reason={}", id, reason);
        return instance;
    }
}
