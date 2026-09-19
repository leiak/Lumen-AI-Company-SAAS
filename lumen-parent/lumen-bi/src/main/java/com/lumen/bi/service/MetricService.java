package com.lumen.bi.service;

import com.lumen.bi.dto.SaveMetricRequest;
import com.lumen.bi.entity.BiMetric;
import com.lumen.bi.mapper.BiMetricMapper;
import com.lumen.bi.sql.SqlGuard;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指标服务。CRUD + parseDefinition 校验 + render 返回 PreparedStatement SQL。
 *
 * <p>安全要求 #11: render 参数化 — 用 {@code :paramName} 占位符,
 * service 层负责替换为 {@code ?} 并返回 params 列表。
 * 上层 (DatasetService/DashboardService) 通过 PreparedStatement 绑定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private final BiMetricMapper metricMapper;

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    /**
     * 安全要求 #12: 跨租户 404。
     */
    public BiMetric get(Long id) {
        UserContext ctx = requireUserContext();
        BiMetric m = metricMapper.selectById(id);
        if (m == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(m.getTenantId()))) {
            throw new ServiceException(404, "Metric not found: " + id);
        }
        return m;
    }

    public List<BiMetric> list() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return metricMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BiMetric>()
                .eq(BiMetric::getTenantId, ctx.getTenantId())
                .eq(BiMetric::getDeleted, 0)
                .orderByDesc(BiMetric::getId));
    }

    public List<BiMetric> findActive() {
        UserContext ctx = requireUserContext();
        List<BiMetric> all = metricMapper.findActive();
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(m -> ctx.getTenantId().equals(m.getTenantId()))
            .toList();
    }

    public List<BiMetric> findByCategory(String category) {
        UserContext ctx = requireUserContext();
        List<BiMetric> all = metricMapper.findByCategory(category);
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(m -> ctx.getTenantId().equals(m.getTenantId()))
            .toList();
    }

    // ---------------------------------------------------------------
    // write
    // ---------------------------------------------------------------

    @Transactional
    public BiMetric save(SaveMetricRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        // 安全要求 #10: parseDefinition 校验 (definition JSON 必须包含 sql/dimensions/measures)
        parseDefinition(req.getDefinition());
        // 校验 sql 是只读 SELECT
        SqlGuard.validateSelectOnly((String) req.getDefinition().get("sql"));

        BiMetric existing = metricMapper.findByCodeAndTenant(req.getCode(), ctx.getTenantId());
        if (existing != null) {
            // update
            if (req.getName() != null) existing.setName(req.getName());
            if (req.getCategory() != null) existing.setCategory(req.getCategory());
            if (req.getDefinition() != null) existing.setDefinition(req.getDefinition());
            existing.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
            existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
            metricMapper.updateById(existing);
            return existing;
        }
        BiMetric m = new BiMetric();
        m.setTenantId(ctx.getTenantId());
        m.setCode(req.getCode());
        m.setName(req.getName());
        m.setCategory(req.getCategory());
        m.setDefinition(req.getDefinition());
        m.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        m.setVersion(1);
        metricMapper.insert(m);
        return m;
    }

    /**
     * 安全要求 #10: parseDefinition 校验必须包含 sql/dimensions/measures。
     * 缺失抛 400。
     */
    public void parseDefinition(Map<String, Object> definition) {
        if (definition == null || definition.isEmpty()) {
            throw new ServiceException(400, "definition is required");
        }
        Object sql = definition.get("sql");
        if (!(sql instanceof String s) || s.isBlank()) {
            throw new ServiceException(400, "definition.sql is required (must be non-empty string)");
        }
        Object dims = definition.get("dimensions");
        if (!(dims instanceof List) || ((List<?>) dims).isEmpty()) {
            throw new ServiceException(400, "definition.dimensions must be non-empty list");
        }
        Object meas = definition.get("measures");
        if (!(meas instanceof List) || ((List<?>) meas).isEmpty()) {
            throw new ServiceException(400, "definition.measures must be non-empty list");
        }
    }

    /**
     * 安全要求 #11: render — 用 PreparedStatement `?` 占位符, 不允许字符串拼接。
     * params 数组按出现顺序传给 PreparedStatement.setObject(i+1, value)。
     *
     * @param metricCode 指标 code
     * @param params     参数值 (按 SQL 中 :name 出现顺序返回)
     * @return RenderResult { sql, params }
     */
    public RenderResult render(String metricCode, Map<String, Object> params) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        BiMetric metric = metricMapper.findByCodeAndTenant(metricCode, ctx.getTenantId());
        if (metric == null) {
            throw new ServiceException(404, "Metric not found: " + metricCode);
        }
        if (!STATUS_ACTIVE.equals(metric.getStatus())) {
            throw new ServiceException(409, "Metric is not active: " + metricCode);
        }
        Map<String, Object> def = metric.getDefinition();
        parseDefinition(def);
        String sql = (String) def.get("sql");
        SqlGuard.validateSelectOnly(sql);

        // 安全要求 #1: 注入 tenant 过滤
        sql = SqlGuard.injectTenantFilter(sql);

        // 安全要求 #11: 替换 :name 为 ? (按出现顺序收集参数值)
        List<Object> ordered = new ArrayList<>();
        Pattern p = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");
        Matcher m = p.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            Object v = params == null ? null : params.get(name);
            if (v == null) {
                // 必须显式提供所有参数 (不允许隐式 null)
                throw new ServiceException(400, "missing param: " + name);
            }
            ordered.add(v);
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        // tenant_id 始终追加到末尾 (injectTenantFilter 注入 ? 占位)
        ordered.add(ctx.getTenantId());

        return new RenderResult(sb.toString(), ordered);
    }

    public record RenderResult(String sql, List<Object> params) {}

    @Transactional
    public void delete(Long id) {
        BiMetric m = get(id); // tenant check
        metricMapper.deleteById(m.getId());
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}