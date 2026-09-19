package com.lumen.bi.service;

import com.lumen.bi.dto.SaveDashboardRequest;
import com.lumen.bi.entity.BiDashboard;
import com.lumen.bi.entity.BiDataset;
import com.lumen.bi.entity.BiWidget;
import com.lumen.bi.mapper.BiDashboardMapper;
import com.lumen.bi.mapper.BiDatasetMapper;
import com.lumen.bi.mapper.BiWidgetMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板服务。CRUD + getFullDashboard 装配流程 + 状态机 (draft→published→archived)。
 *
 * <p>安全要求 #6: getFullDashboard 必须 checkAccess; 失败 403。
 * 安全要求 #8: Dashboard publish 后 layout 不能改 (状态机)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    private final BiDashboardMapper dashboardMapper;
    private final BiWidgetMapper widgetMapper;
    private final BiDatasetMapper datasetMapper;
    private final PermissionService permissionService;

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    public BiDashboard get(Long id) {
        UserContext ctx = requireUserContext();
        BiDashboard d = dashboardMapper.selectById(id);
        if (d == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(d.getTenantId()))) {
            throw new ServiceException(404, "Dashboard not found: " + id);
        }
        return d;
    }

    public List<BiDashboard> list() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return dashboardMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BiDashboard>()
                .eq(BiDashboard::getTenantId, ctx.getTenantId())
                .eq(BiDashboard::getDeleted, 0)
                .orderByDesc(BiDashboard::getId));
    }

    public List<BiDashboard> findPublished() {
        UserContext ctx = requireUserContext();
        List<BiDashboard> all = dashboardMapper.findPublished();
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(d -> ctx.getTenantId().equals(d.getTenantId()))
            .toList();
    }

    public List<BiDashboard> findByStatus(String status) {
        UserContext ctx = requireUserContext();
        List<BiDashboard> all = dashboardMapper.findByStatus(status);
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(d -> ctx.getTenantId().equals(d.getTenantId()))
            .toList();
    }

    // ---------------------------------------------------------------
    // write
    // ---------------------------------------------------------------

    @Transactional
    public BiDashboard save(SaveDashboardRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        BiDashboard existing = dashboardMapper.findByCodeAndTenant(req.getCode(), ctx.getTenantId());
        if (existing != null) {
            // 安全要求 #8: published 后 layout 不能改
            if (STATUS_PUBLISHED.equals(existing.getStatus())
                || STATUS_ARCHIVED.equals(existing.getStatus())) {
                throw new ServiceException(409,
                    "Dashboard is " + existing.getStatus() + "; cannot modify layout");
            }
            if (req.getName() != null) existing.setName(req.getName());
            if (req.getLayout() != null) existing.setLayout(req.getLayout());
            existing.setStatus(req.getStatus() == null ? STATUS_DRAFT : req.getStatus());
            dashboardMapper.updateById(existing);
            return existing;
        }
        BiDashboard d = new BiDashboard();
        d.setTenantId(ctx.getTenantId());
        d.setCode(req.getCode());
        d.setName(req.getName());
        d.setLayout(req.getLayout());
        d.setStatus(req.getStatus() == null ? STATUS_DRAFT : req.getStatus());
        dashboardMapper.insert(d);
        return d;
    }

    /**
     * 安全要求 #8: publish (draft → published)。publishedAt 记录时间。
     * 已发布 / 已归档则 409。
     */
    @Transactional
    public BiDashboard publish(Long id) {
        BiDashboard d = get(id);
        if (STATUS_PUBLISHED.equals(d.getStatus())) {
            throw new ServiceException(409, "Dashboard already published: " + id);
        }
        if (STATUS_ARCHIVED.equals(d.getStatus())) {
            throw new ServiceException(409, "Dashboard is archived; cannot publish: " + id);
        }
        d.setStatus(STATUS_PUBLISHED);
        d.setPublishedAt(LocalDateTime.now());
        dashboardMapper.updateById(d);
        log.info("Dashboard published id={} by={}", id, UserContextHolder.get().getUserId());
        return d;
    }

    /**
     * archive (任何状态 → archived)。
     */
    @Transactional
    public BiDashboard archive(Long id) {
        BiDashboard d = get(id);
        d.setStatus(STATUS_ARCHIVED);
        dashboardMapper.updateById(d);
        log.info("Dashboard archived id={}", id);
        return d;
    }

    @Transactional
    public void delete(Long id) {
        BiDashboard d = get(id);
        dashboardMapper.deleteById(d.getId());
    }

    // ---------------------------------------------------------------
    // getFullDashboard — 装配 dashboard + 所有 widget + 关联 dataset 预览
    // ---------------------------------------------------------------

    /**
     * 安全要求 #6: getFullDashboard 必须 checkAccess; 失败 403。
     *
     * <p>装配流程:
     * 1. 加载 dashboard (tenant check)
     * 2. checkAccess (admin 直接通过; 否则查 bi_permission)
     * 3. 加载所有 widget (按 dashboardId)
     * 4. 加载关联 dataset (按 widget.datasetId); 调用 DatasetService.preview(1, 100) 拿 preview
     * 5. 组装结果: {dashboard, widgets, datasetPreviews}</p>
     */
    public Map<String, Object> getFullDashboard(Long dashboardId) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        BiDashboard dashboard = get(dashboardId);

        // 安全要求 #6: checkAccess
        PermissionService.Principal principal = permissionService.currentPrincipal();
        boolean accessible = permissionService.checkAccess(
            PermissionService.RESOURCE_DASHBOARD,
            dashboardId,
            PermissionService.PRINCIPAL_USER,
            principal.userId());
        if (!accessible) {
            throw new ServiceException(403, "No access to dashboard: " + dashboardId);
        }

        List<BiWidget> widgets = widgetMapper.findByDashboard(dashboardId).stream()
            .filter(w -> ctx.getTenantId().equals(w.getTenantId()))
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("dashboard", dashboard);
        result.put("widgets", widgets);

        // 加载关联 dataset 预览 (按 widget.datasetId 去重)
        List<Long> datasetIds = widgets.stream()
            .map(BiWidget::getDatasetId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, BiDataset> datasets = new HashMap<>();
        for (Long id : datasetIds) {
            BiDataset ds = datasetMapper.selectById(id);
            if (ds != null && ctx.getTenantId().equals(ds.getTenantId())) {
                datasets.put(id, ds);
            }
        }
        result.put("datasets", datasets);
        return result;
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}