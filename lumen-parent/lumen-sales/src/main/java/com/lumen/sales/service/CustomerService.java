package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 客户服务。公海/私海模型。
 * <p>
 * 公海 (status=in_pool):所有人可抢;抢后 owner=当前用户,status=private。
 * 私海 (status=private/active):只有 owner 或 admin/sales_admin 可改。
 * 公海自动回收:P5 定时任务(90 天未跟进自动回到公海)— 当前留 TODO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    // 状态常量
    public static final String STATUS_IN_POOL = "in_pool";
    public static final String STATUS_PRIVATE = "private";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_LOST = "lost";

    public static final String LEVEL_A = "A";
    public static final String LEVEL_B = "B";
    public static final String LEVEL_C = "C";
    public static final String LEVEL_D = "D";

    private static final Set<String> ALLOWED_LEVELS = Set.of(LEVEL_A, LEVEL_B, LEVEL_C, LEVEL_D);
    private static final Set<String> ALLOWED_STATUSES = Set.of(
        STATUS_IN_POOL, STATUS_PRIVATE, STATUS_ACTIVE, STATUS_LOST);

    private final CustomerMapper customerMapper;

    /** 分页查询 — 默认按 owner_user_id 过滤,sales_admin 角色可见全部。 */
    public IPage<Customer> page(int pageNum, int pageSize, String name, String status, Long ownerLevel) {
        UserContext ctx = requireContext();
        var w = new LambdaQueryWrapper<Customer>().orderByDesc(Customer::getId);
        if (name != null && !name.isBlank()) {
            w.like(Customer::getName, name);
        }
        if (status != null && !status.isBlank()) {
            w.eq(Customer::getStatus, status);
        }
        boolean isAdminLike = hasAnyRole(ctx, "super_admin", "admin", "sales_admin");
        Long ownerFilter = ownerLevel;
        if (ownerFilter == null && !isAdminLike) {
            ownerFilter = ctx.getUserId();
        }
        if (ownerFilter != null) {
            w.eq(Customer::getOwnerUserId, ownerFilter);
        }
        return customerMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Customer get(Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Customer not found: " + id);
        UserContext ctx = requireContext();
        if (!isSuperAdmin(ctx)) {
            if (!ctx.getTenantId().equals(c.getTenantId())) {
                throw new ServiceException(404, "Customer not found: " + id);
            }
        }
        return c;
    }

    /**
     * 创建客户。新建默认进入公海 (status=in_pool, owner_user_id=null)。
     * 管理员也可直接创建为私海客户。
     */
    @Transactional
    public Customer save(Customer req) {
        UserContext ctx = requireContext();
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        if (req.getLevel() != null && !ALLOWED_LEVELS.contains(req.getLevel())) {
            throw new ServiceException(400, "level must be one of A/B/C/D");
        }
        Customer toCreate = new Customer();
        toCreate.setCode(req.getCode());
        toCreate.setName(req.getName());
        toCreate.setLevel(req.getLevel());
        toCreate.setSource(req.getSource());
        toCreate.setIndustry(req.getIndustry());
        toCreate.setScale(req.getScale());
        toCreate.setAddress(req.getAddress());
        toCreate.setTenantId(ctx.getTenantId());
        toCreate.setOwnerUserId(null);
        toCreate.setStatus(STATUS_IN_POOL);
        try {
            customerMapper.insert(toCreate);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Customer code already exists: " + req.getCode(), ex);
        }
        return toCreate;
    }

    /**
     * 客户等级修改 — 仅 super_admin / admin / sales_admin 可改。
     */
    @Transactional
    public Customer updateLevel(Long id, String newLevel) {
        UserContext ctx = requireContext();
        if (!hasAnyRole(ctx, "super_admin", "admin", "sales_admin")) {
            throw new ServiceException(403, "Only admin can change customer level");
        }
        if (!ALLOWED_LEVELS.contains(newLevel)) {
            throw new ServiceException(400, "level must be one of A/B/C/D");
        }
        Customer c = get(id);
        c.setLevel(newLevel);
        customerMapper.updateById(c);
        return c;
    }

    /** 公海列表。 */
    public java.util.List<Customer> findInPool(int limit) {
        UserContext ctx = requireContext();
        return customerMapper.findInPool(ctx.getTenantId(), Math.min(Math.max(limit, 1), 200));
    }

    /**
     * 从公海抢客户 — 仅当 status=in_pool。抢后 owner=ctx.userId,status=private。
     */
    @Transactional
    public Customer claimFromPool(Long id) {
        UserContext ctx = requireContext();
        Customer c = get(id);
        if (!STATUS_IN_POOL.equals(c.getStatus())) {
            throw new ServiceException(409, "Customer is not in pool: " + c.getStatus());
        }
        c.setOwnerUserId(ctx.getUserId());
        c.setStatus(STATUS_PRIVATE);
        customerMapper.updateById(c);
        log.info("Customer {} claimed by user {}", id, ctx.getUserId());
        return c;
    }

    /**
     * 归还客户到公海 — 仅 owner 或 admin 可触发。
     */
    @Transactional
    public Customer returnToPool(Long id, String reason) {
        UserContext ctx = requireContext();
        Customer c = get(id);
        assertOwnerOrAdmin(c, ctx, "return");
        c.setStatus(STATUS_IN_POOL);
        c.setOwnerUserId(null);
        if (reason != null && !reason.isBlank()) {
            c.setLostReason(reason);
        }
        customerMapper.updateById(c);
        return c;
    }

    /** 分配客户 — 仅 super_admin / admin / sales_admin。 */
    @Transactional
    public Customer assign(Long id, Long toUserId) {
        UserContext ctx = requireContext();
        if (!hasAnyRole(ctx, "super_admin", "admin", "sales_admin")) {
            throw new ServiceException(403, "Only admin can assign customers");
        }
        if (toUserId == null) {
            throw new ServiceException(400, "toUserId is required");
        }
        Customer c = get(id);
        c.setOwnerUserId(toUserId);
        if (STATUS_IN_POOL.equals(c.getStatus())) {
            c.setStatus(STATUS_PRIVATE);
        }
        customerMapper.updateById(c);
        return c;
    }

    /** 公海自动回收 — TODO P5 定时任务 (90 天未跟进自动回公海)。 */
    public void recycleStaleCustomers() {
        log.info("[TODO P5] recycle stale customers to pool");
    }

    /** Owner 或 admin 校验。 */
    public void assertOwnerOrAdmin(Customer c, UserContext ctx, String action) {
        boolean isAdmin = hasAnyRole(ctx, "super_admin", "admin", "sales_admin");
        Long uid = ctx.getUserId();
        if (!isAdmin && (uid == null || !uid.equals(c.getOwnerUserId()))) {
            throw new ServiceException(403,
                "Only owner or admin can " + action + " customer");
        }
    }

    // -------- helpers --------

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }

    public boolean hasAnyRole(UserContext ctx, String... roles) {
        if (ctx == null || ctx.getRoles() == null) return false;
        for (String r : roles) {
            if (ctx.getRoles().contains(r)) return true;
        }
        return false;
    }

    public boolean isSuperAdmin(UserContext ctx) {
        return ctx != null && ctx.getRoles() != null && ctx.getRoles().contains("super_admin");
    }

    public Set<String> allowedStatuses() {
        return ALLOWED_STATUSES;
    }

    public Set<String> allowedLevels() {
        return ALLOWED_LEVELS;
    }
}