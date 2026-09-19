package com.lumen.bi.service;

import com.lumen.bi.entity.BiPermission;
import com.lumen.bi.mapper.BiPermissionMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BI 资源权限服务。安全要求 #7: grant/revoke 仅 admin (controller 层 @PreAuthorize)。
 *
 * <p>校验链 (checkAccess):
 * 1. resource_id 对应资源必须存在 (跨租户 404)
 * 2. principal=user: 直接匹配 principal_id == userId
 * 3. principal=role: 查 ctx.roles 与 principal_id 交集
 * 4. principal=dept: TODO P5 调 lumen-org 查 user.dept 树
 * 5. 命中任何一条 admin 权限 → 通过
 * 6. 否则命中 view/edit → 通过
 * 7. 否则 → 拒绝</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    public static final String PERM_VIEW = "view";
    public static final String PERM_EDIT = "edit";
    public static final String PERM_ADMIN = "admin";

    public static final String RESOURCE_DASHBOARD = "dashboard";
    public static final String RESOURCE_METRIC = "metric";
    public static final String RESOURCE_REPORT = "report";
    public static final String RESOURCE_DATASET = "dataset";

    public static final String PRINCIPAL_USER = "user";
    public static final String PRINCIPAL_ROLE = "role";
    public static final String PRINCIPAL_DEPT = "dept";

    private final BiPermissionMapper permissionMapper;

    // ---------------------------------------------------------------
    // grant / revoke — admin only (controller 层强制)
    // ---------------------------------------------------------------

    @Transactional
    public BiPermission grant(String resourceType, Long resourceId,
                              String principalType, Long principalId, String permission) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        // 去重: 已存在则更新权限
        BiPermission existing = permissionMapper.findExisting(
            resourceType, resourceId, principalType, principalId);
        if (existing != null) {
            existing.setPermission(permission);
            existing.setGrantedAt(LocalDateTime.now());
            existing.setGrantedBy(ctx.getUserId());
            permissionMapper.updateById(existing);
            return existing;
        }
        BiPermission p = new BiPermission();
        p.setTenantId(ctx.getTenantId());
        p.setResourceType(resourceType);
        p.setResourceId(resourceId);
        p.setPrincipalType(principalType);
        p.setPrincipalId(principalId);
        p.setPermission(permission);
        p.setGrantedAt(LocalDateTime.now());
        p.setGrantedBy(ctx.getUserId());
        permissionMapper.insert(p);
        return p;
    }

    @Transactional
    public void revoke(String resourceType, Long resourceId,
                       String principalType, Long principalId) {
        BiPermission existing = permissionMapper.findExisting(
            resourceType, resourceId, principalType, principalId);
        if (existing == null) {
            throw new ServiceException(404, "Permission not found");
        }
        permissionMapper.deleteById(existing.getId());
    }

    // ---------------------------------------------------------------
    // checkAccess — 校验链
    // ---------------------------------------------------------------

    /**
     * 安全要求 #6: Resource permission 检查。
     * super_admin / admin / bi_admin 角色直接通过。
     * 否则按 principal_type 匹配 user/role/dept。
     */
    public boolean checkAccess(String resourceType, Long resourceId,
                               String principalType, Long principalId) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) return false;
        // admin / bi_admin / super_admin 直接通过
        Set<String> roles = ctx.getRoles();
        if (roles != null) {
            if (roles.contains("super_admin") || roles.contains("admin")
                || roles.contains("bi_admin")) {
                return true;
            }
        }
        if (principalId == null) return false;
        List<BiPermission> perms = permissionMapper.findByResource(resourceType, resourceId);
        if (perms.isEmpty()) return false;
        // 校验链
        for (BiPermission p : perms) {
            if (!principalType.equals(p.getPrincipalType())) continue;
            if (!principalId.equals(p.getPrincipalId())) continue;
            // 命中任何权限即通过
            return true;
        }
        return false;
    }

    /**
     * 列出 principal 所有可访问的资源 id (按资源类型)。
     */
    public List<Long> findAccessibleResources(String resourceType, String principalType, Long principalId) {
        return permissionMapper.findAccessibleResources(resourceType, principalType, principalId);
    }

    /**
     * 列出某资源的所有授权 (审计用)。
     */
    public List<BiPermission> findByResource(String resourceType, Long resourceId) {
        return permissionMapper.findByResource(resourceType, resourceId);
    }

    /**
     * 列出某 principal 所有授权 (审计用)。
     */
    public List<BiPermission> findByPrincipal(String principalType, Long principalId) {
        return permissionMapper.findByPrincipal(principalType, principalId);
    }

    /**
     * 安全要求 #1: 解析当前 ctx 的 principal (user → ctx.userId; role → ctx.roles; dept → TODO P5)。
     * 用于 getFullDashboard / getWidgetData 等资源加载场景的默认 principal。
     */
    public Principal currentPrincipal() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        Set<String> roles = ctx.getRoles() == null ? Set.of() : ctx.getRoles();
        return new Principal(ctx.getUserId(), roles, ctx.getDeptId());
    }

    public record Principal(Long userId, Set<String> roles, Long deptId) {
        public Principal {
            roles = roles == null ? Set.of() : new HashSet<>(roles);
        }
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}