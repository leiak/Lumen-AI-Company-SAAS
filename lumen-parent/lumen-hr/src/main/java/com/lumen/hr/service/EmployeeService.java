package com.lumen.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.SaveEmployeeRequest;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrTransfer;
import com.lumen.hr.event.EmployeeTransferredEvent;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Set;

/**
 * Employee lifecycle: CRUD + transfer (with audit-trail row).
 *
 * <p>Security invariants enforced here:</p>
 * <ul>
 *   <li>Tenant isolation: every read/write is scoped via TenantLineInnerInterceptor;
 *       direct service-level guards raise 401 when the context is missing.</li>
 *   <li>Cross-tenant access returns 404 (not 403) to avoid existence disclosure.</li>
 *   <li>Transfer writes a {@link HrTransfer} history row AND updates the employee's
 *       {@code deptId}/{@code postId} (audit trail, not silent overwrite).</li>
 *   <li>{@code idCardEnc}/{@code mobileEnc} are encrypted at the field level via
 *       {@code EncryptedStringTypeHandler}; the service treats them as plain strings.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    /** Status constants. */
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_PROBATION = 1;
    public static final int STATUS_RESIGNED = 2;
    public static final int STATUS_SUSPENDED = 3;

    /** Transfer flow status. */
    public static final int TRANSFER_STATUS_APPROVED = 1;
    public static final int TRANSFER_STATUS_EFFECTIVE = 3;

    private final HrEmployeeMapper employeeMapper;
    private final HrTransferMapper transferMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ---------------------------------------------------------------
    // Read paths
    // ---------------------------------------------------------------

    public IPage<HrEmployee> list(int pageNum, int pageSize, String keyword, Long deptId, Integer status) {
        requireTenant();
        var w = new LambdaQueryWrapper<HrEmployee>().orderByDesc(HrEmployee::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(HrEmployee::getName, keyword)
                .or().like(HrEmployee::getCode, keyword));
        }
        if (deptId != null) w.eq(HrEmployee::getDeptId, deptId);
        if (status != null) w.eq(HrEmployee::getStatus, status);
        return employeeMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    /**
     * Fetch by id, tenant-scoped. Cross-tenant access returns 404 (not 403) to avoid
     * existence disclosure.
     */
    public HrEmployee getById(Long id) {
        requireTenant();
        HrEmployee e = employeeMapper.selectById(id);
        if (e == null) throw new ServiceException(404, "Employee not found: " + id);
        ensureSameTenant(e);
        return e;
    }

    /**
     * Look up the employee record tied to the current user. Used by the
     * {@code /hr/employee/profile} endpoint, which intentionally bypasses the admin-only
     * RBAC guards on the list / save paths.
     */
    public HrEmployee getProfile() {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "No user context");
        requireTenant();
        HrEmployee e = employeeMapper.findByUserId(uid);
        if (e == null) throw new ServiceException(404, "Employee profile not found");
        return e;
    }

    public java.util.List<HrEmployee> findByDeptAndStatus(Long deptId, Integer status) {
        requireTenant();
        return employeeMapper.findByDeptAndStatus(deptId, status);
    }

    // ---------------------------------------------------------------
    // Write paths
    // ---------------------------------------------------------------

    @Transactional
    public HrEmployee save(SaveEmployeeRequest req) {
        requireTenant();
        if (req.getId() == null) {
            return create(req);
        }
        return update(req);
    }

    private HrEmployee create(SaveEmployeeRequest req) {
        HrEmployee e = new HrEmployee();
        e.setUserId(req.getUserId());
        e.setCode(req.getCode());
        e.setName(req.getName());
        e.setIdCardEnc(req.getIdCardEnc());
        e.setMobileEnc(req.getMobileEnc());
        e.setDeptId(req.getDeptId());
        e.setPostId(req.getPostId());
        e.setLevelId(req.getLevelId());
        e.setStatus(req.getStatus() != null ? req.getStatus() : STATUS_PROBATION);
        e.setTenantId(currentTenantId());
        employeeMapper.insert(e);
        log.info("Created employee id={} code={}", e.getId(), e.getCode());
        return e;
    }

    private HrEmployee update(SaveEmployeeRequest req) {
        HrEmployee existing = getById(req.getId());
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getIdCardEnc() != null) existing.setIdCardEnc(req.getIdCardEnc());
        if (req.getMobileEnc() != null) existing.setMobileEnc(req.getMobileEnc());
        if (req.getDeptId() != null) existing.setDeptId(req.getDeptId());
        if (req.getPostId() != null) existing.setPostId(req.getPostId());
        if (req.getLevelId() != null) existing.setLevelId(req.getLevelId());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        employeeMapper.updateById(existing);
        log.info("Updated employee id={}", existing.getId());
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        HrEmployee existing = getById(id);
        // Soft-delete only — @TableLogic on deleted column flips to 1.
        employeeMapper.deleteById(existing.getId());
        log.info("Soft-deleted employee id={}", existing.getId());
    }

    /**
     * Restore a soft-deleted employee (admin only — RBAC enforced at controller).
     * MyBatis-Plus doesn't expose restore directly; do it via a manual UPDATE.
     */
    @Transactional
    public HrEmployee restore(Long id) {
        requireTenant();
        // Use a direct UPDATE bypassing @TableLogic: select with deleted-only filter first.
        HrEmployee e = employeeMapper.selectById(id);
        if (e != null) {
            throw new ServiceException(409, "Employee is not deleted: " + id);
        }
        // Re-select with @InterceptorIgnore would be cleaner; for P4 stub we keep it simple
        // and require the caller to re-create if needed. Restore is a TODO.
        throw new ServiceException(501, "restore() not yet implemented; recreate the employee instead");
    }

    /**
     * Transfer an employee: write a {@link HrTransfer} audit row + update the employee
     * record atomically. Publishes {@link EmployeeTransferredEvent} so downstream
     * services (auth / org / message) can react.
     */
    @Transactional
    public HrEmployee transfer(Long employeeId, Long toDeptId, Long toPostId,
                               java.time.LocalDate effectiveAt) {
        requireTenant();
        if (employeeId == null) throw new ServiceException(400, "employeeId is required");
        if (toDeptId == null) throw new ServiceException(400, "toDeptId is required");
        if (toPostId == null) throw new ServiceException(400, "toPostId is required");
        if (effectiveAt == null) throw new ServiceException(400, "effectiveAt is required");

        HrEmployee e = employeeMapper.selectById(employeeId);
        if (e == null) throw new ServiceException(404, "Employee not found: " + employeeId);
        ensureSameTenant(e);

        // 1. Audit trail — append, never overwrite.
        HrTransfer tr = new HrTransfer();
        tr.setEmployeeId(e.getId());
        tr.setFromDeptId(e.getDeptId());
        tr.setToDeptId(toDeptId);
        tr.setFromPostId(e.getPostId());
        tr.setToPostId(toPostId);
        tr.setEffectiveAt(effectiveAt);
        tr.setStatus(TRANSFER_STATUS_APPROVED);
        tr.setTenantId(currentTenantId());
        transferMapper.insert(tr);

        // 2. Apply the transfer to the employee.
        e.setDeptId(toDeptId);
        e.setPostId(toPostId);
        employeeMapper.updateById(e);

        // 3. Publish the event so auth/org can re-cache permissions.
        // TODO: cross-service event bus (Nacos + RocketMQ) — currently ApplicationEventPublisher
        // only delivers to in-process listeners. Cross-service wiring is a P4 deliverable.
        eventPublisher.publishEvent(EmployeeTransferredEvent.builder()
            .employeeId(e.getId())
            .fromDeptId(tr.getFromDeptId())
            .toDeptId(tr.getToDeptId())
            .fromPostId(tr.getFromPostId())
            .toPostId(tr.getToPostId())
            .effectiveAt(effectiveAt)
            .operatorUserId(UserContextHolder.getUserId())
            .tenantId(currentTenantId())
            .build());

        log.info("Transferred employee id={} {}->{} / {}->{} effectiveAt={}",
            e.getId(), tr.getFromDeptId(), tr.getFromDeptId(), tr.getFromPostId(), tr.getToPostId(),
            effectiveAt);
        return e;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Refuse to act without a tenant — controllers' RBAC only proves the caller is
     * authenticated, not that the JWT carried a tenant_id.
     */
    private void requireTenant() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
    }

    private long currentTenantId() {
        Long t = UserContextHolder.getTenantId();
        if (t == null) throw new ServiceException(401, "Missing tenant context");
        return t;
    }

    private void ensureSameTenant(HrEmployee e) {
        UserContext ctx = UserContextHolder.get();
        Set<String> roles = ctx == null ? null : ctx.getRoles();
        boolean isSuperAdmin = roles != null && roles.contains("super_admin");
        if (isSuperAdmin) return;
        Long ctxTenant = ctx == null ? null : ctx.getTenantId();
        if (ctxTenant == null || e.getTenantId() == null
            || !ctxTenant.equals(e.getTenantId())) {
            // Cross-tenant access must surface as 404 to avoid existence disclosure.
            throw new ServiceException(404, "Employee not found: " + e.getId());
        }
    }

    /** Read-only check used by tests. */
    public HrEmployee peekById(Long id) {
        return employeeMapper.selectById(id);
    }

    /** Read-only check used by tests. */
    public java.util.Set<String> rolesOfCurrentUser() {
        UserContext ctx = UserContextHolder.get();
        return ctx == null || ctx.getRoles() == null
            ? Collections.emptySet() : ctx.getRoles();
    }
}
