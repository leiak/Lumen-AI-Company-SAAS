package com.lumen.bi.sql;

import com.lumen.common.core.exception.ServiceException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 安全白名单校验器。
 *
 * <p>安全要求 #3 / #10:
 * - SQL 必须 SELECT 开头 (case-insensitive)
 * - 禁止 INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/REVOKE
 * - 用 `;` 分割取第一条 SQL, 拒绝多语句</p>
 *
 * <p>校验流程:
 * 1. trim + 去除首尾分号
 * 2. 拒绝包含 ; (避免堆叠 SQL 注入)
 * 3. 开头匹配 ^\s*SELECT\b (大小写不敏感)
 * 4. 拒绝黑名单关键字 (用 \b 整词边界, 避免误伤列名)</p>
 */
public final class SqlGuard {

    /** SELECT 开头 (允许前置空白/换行) */
    private static final Pattern SELECT_HEAD = Pattern.compile(
        "^\\s*SELECT\\b.*$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 危险关键字 (整词匹配, 防止 WHERE updated_at 这种列名误伤) */
    private static final Set<String> FORBIDDEN = Set.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER",
        "TRUNCATE", "CREATE", "GRANT", "REVOKE",
        "EXEC", "EXECUTE", "CALL", "MERGE", "REPLACE",
        "LOCK", "UNLOCK", "RENAME", "SET"
    );

    /** Ad-hoc LIMIT 上限 */
    public static final int AD_HOC_LIMIT = 1000;

    private SqlGuard() {}

    /**
     * 校验 SQL 是只读 SELECT (用于 AdHoc / Dataset / Metric definition)。
     * 不通过抛 ServiceException(400, msg)。
     */
    public static void validateSelectOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new ServiceException(400, "sql is empty");
        }
        String trimmed = sql.trim();
        // 去除末尾分号
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        // 多语句拒绝
        if (trimmed.contains(";")) {
            throw new ServiceException(400,
                "multiple SQL statements are not allowed");
        }
        // 必须 SELECT 开头
        if (!SELECT_HEAD.matcher(trimmed).matches()) {
            throw new ServiceException(400,
                "sql must start with SELECT (read-only)");
        }
        // 关键字黑名单
        String upper = trimmed.toUpperCase();
        for (String kw : FORBIDDEN) {
            // 整词边界: 前面是 \b (非字母数字下划线), 后面同理
            Pattern p = Pattern.compile("(?<![A-Z0-9_])" + kw + "(?![A-Z0-9_])");
            if (p.matcher(upper).find()) {
                throw new ServiceException(400,
                    "forbidden keyword in sql: " + kw);
            }
        }
    }

    /**
     * 给 SELECT 追加 LIMIT (若已有 LIMIT 则不追加)。
     * 安全要求 #5: AdHoc 必须强制 LIMIT 1000。
     */
    public static String enforceLimit(String sql, int limit) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (Pattern.compile("(?i)\\bLIMIT\\b\\s+\\d+").matcher(trimmed).find()) {
            return trimmed;
        }
        return trimmed + "\nLIMIT " + limit;
    }

    /**
     * 注入 tenant_id 过滤条件。
     * 安全要求 #1: BI 数据查询必须 filter tenant_id 即使是跨服务 SQL。
     * 简单实现: 在 WHERE 子句末尾追加 AND tenant_id = ?;
     * 若 SQL 无 WHERE 则添加 WHERE tenant_id = ?;
     */
    public static String injectTenantFilter(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        // 已有 tenant_id 过滤则不注入 (避免重复)
        if (Pattern.compile("(?i)\\btenant_id\\b\\s*=").matcher(trimmed).find()) {
            return trimmed;
        }
        // 找 WHERE 位置
        Pattern wherePattern = Pattern.compile("(?i)\\bWHERE\\b");
        java.util.regex.Matcher m = wherePattern.matcher(trimmed);
        if (m.find()) {
            // 在 WHERE 之后第一个完整条件前注入 (简化: 直接在末尾加 AND tenant_id = ?)
            // 这里采用最安全做法: 在末尾追加 AND tenant_id = ?
            return trimmed + "\nAND tenant_id = ?";
        }
        return trimmed + "\nWHERE tenant_id = ?";
    }
}