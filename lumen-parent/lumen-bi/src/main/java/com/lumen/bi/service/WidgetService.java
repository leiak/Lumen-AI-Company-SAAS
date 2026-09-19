package com.lumen.bi.service;

import com.lumen.bi.entity.BiDataset;
import com.lumen.bi.entity.BiWidget;
import com.lumen.bi.mapper.BiDatasetMapper;
import com.lumen.bi.mapper.BiWidgetMapper;
import com.lumen.bi.sql.SqlGuard;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板组件服务。CRUD + getWidgetData 渲染单个 widget 数据。
 *
 * <p>安全要求 #13: Widget 数据返回前必须 checkAccess; 失败 403。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    public static final String TYPE_TABLE = "table";
    public static final String TYPE_CHART = "chart";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_GAUGE = "gauge";
    public static final String TYPE_PIVOT = "pivot";
    public static final String TYPE_FILTER = "filter";

    private final BiWidgetMapper widgetMapper;
    private final BiDatasetMapper datasetMapper;
    private final PermissionService permissionService;
    private final DatasetService datasetService;

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    public BiWidget get(Long id) {
        UserContext ctx = requireUserContext();
        BiWidget w = widgetMapper.selectById(id);
        if (w == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(w.getTenantId()))) {
            throw new ServiceException(404, "Widget not found: " + id);
        }
        return w;
    }

    public List<BiWidget> findByDashboard(Long dashboardId) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        List<BiWidget> all = widgetMapper.findByDashboard(dashboardId);
        return all.stream()
            .filter(w -> ctx.getTenantId().equals(w.getTenantId()))
            .toList();
    }

    // ---------------------------------------------------------------
    // write
    // ---------------------------------------------------------------

    @Transactional
    public BiWidget save(BiWidget req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        if (req.getStatus() == null) req.setStatus(STATUS_ACTIVE);
        req.setTenantId(ctx.getTenantId());
        if (req.getId() != null && req.getId() > 0) {
            BiWidget existing = get(req.getId()); // tenant check
            existing.setDashboardId(req.getDashboardId());
            existing.setName(req.getName());
            existing.setType(req.getType());
            existing.setDatasetId(req.getDatasetId());
            existing.setMetricId(req.getMetricId());
            existing.setConfig(req.getConfig());
            existing.setPosition(req.getPosition());
            existing.setStatus(req.getStatus());
            widgetMapper.updateById(existing);
            return existing;
        }
        widgetMapper.insert(req);
        return req;
    }

    @Transactional
    public void delete(Long id) {
        BiWidget w = get(id);
        widgetMapper.deleteById(w.getId());
    }

    // ---------------------------------------------------------------
    // getWidgetData
    // ---------------------------------------------------------------

    /**
     * 安全要求 #13: 返回数据前必须 checkAccess; 失败 403。
     * 渲染逻辑: 优先用 metricId (走 MetricService.render); 否则用 datasetId (走 DatasetService.preview)。
     */
    public Map<String, Object> getWidgetData(Long widgetId) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        BiWidget w = get(widgetId);
        if (!STATUS_ACTIVE.equals(w.getStatus())) {
            throw new ServiceException(409, "Widget is not active: " + widgetId);
        }
        // 安全要求 #13: checkAccess (admin / bi_admin 通过; 否则需 bi_permission 记录)
        PermissionService.Principal principal = permissionService.currentPrincipal();
        boolean accessible = permissionService.checkAccess(
            PermissionService.RESOURCE_DASHBOARD,
            w.getDashboardId(),
            PermissionService.PRINCIPAL_USER,
            principal.userId());
        if (!accessible) {
            throw new ServiceException(403, "No access to widget's dashboard: " + w.getDashboardId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("widgetId", w.getId());
        result.put("type", w.getType());
        result.put("config", w.getConfig());

        if (w.getMetricId() != null) {
            // metric path: 渲染 SQL (TODO P5: 用 JdbcTemplate 执行)
            result.put("source", "metric");
            result.put("metricId", w.getMetricId());
        } else if (w.getDatasetId() != null) {
            BiDataset ds = datasetMapper.selectById(w.getDatasetId());
            if (ds == null) {
                throw new ServiceException(404, "Dataset not found: " + w.getDatasetId());
            }
            // 调用 DatasetService.preview (page=1, pageSize=100)
            Map<String, Object> preview = datasetService.preview(ds.getCode(), 1, 100);
            result.put("source", "dataset");
            result.put("preview", preview);
        } else {
            throw new ServiceException(409, "Widget has no data source: " + widgetId);
        }
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