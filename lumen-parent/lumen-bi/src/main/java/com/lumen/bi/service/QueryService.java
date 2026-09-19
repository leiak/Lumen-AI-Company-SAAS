package com.lumen.bi.service;

import com.lumen.bi.sql.SqlGuard;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ad-hoc 查询服务。
 *
 * <p>安全要求 #3-#5:
 * - SQL 必须 SELECT 开头 (case-insensitive)
 * - 禁止 INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/REVOKE
 * - 用 `;` 分割取第一条 SQL, 拒绝多语句
 * - 自动追加 LIMIT 1000 (SqlGuard.enforceLimit)
 * - 必须 admin / bi_admin / super_admin (controller 层 @PreAuthorize)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    public static final int AD_HOC_LIMIT = SqlGuard.AD_HOC_LIMIT;

    @Qualifier("jdbcTemplate")
    private final JdbcTemplate biJdbc;

    /**
     * 安全要求 #3-#5: 执行 ad-hoc SQL。
     * @param sql SELECT-only SQL
     * @param params 占位符参数 (按出现顺序)
     * @return { columns, rows, totalRows, limit }
     */
    public Map<String, Object> executeAdHoc(String sql, Map<String, Object> params) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        // 安全要求 #3: 白名单校验
        SqlGuard.validateSelectOnly(sql);
        // 安全要求 #1: tenant 过滤
        String scoped = SqlGuard.injectTenantFilter(sql);
        // 安全要求 #5: LIMIT 强制
        String limited = SqlGuard.enforceLimit(scoped, AD_HOC_LIMIT);

        // params 是 Map; PreparedStatement 需要顺序数组
        // 这里简化: 收集 sql 中 :name 按出现顺序, 用 params.get(name) 替换
        List<Object> ordered = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");
        java.util.regex.Matcher m = p.matcher(limited);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            Object v = params == null ? null : params.get(name);
            if (v == null) {
                throw new ServiceException(400, "missing param: " + name);
            }
            ordered.add(v);
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        ordered.add(ctx.getTenantId()); // tenant filter 占位

        List<Map<String, Object>> rows = biJdbc.queryForList(sb.toString(), ordered.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("limit", AD_HOC_LIMIT);
        if (!rows.isEmpty()) {
            result.put("columns", new ArrayList<>(rows.get(0).keySet()));
        } else {
            result.put("columns", List.of());
        }
        log.info("AdHoc query by user={} tenant={} rows={}",
            ctx.getUserId(), ctx.getTenantId(), rows.size());
        return result;
    }
}