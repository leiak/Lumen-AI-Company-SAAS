package com.lumen.bi.sql;

import com.lumen.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 白名单校验器测试。
 * 安全要求 #3 / #10。
 */
class SqlGuardTest {

    // ---------- validateSelectOnly: 合法 SELECT ----------

    @Test
    void validate_simpleSelect_succeeds() {
        assertDoesNotThrow(() ->
            SqlGuard.validateSelectOnly("SELECT * FROM bi_metric"));
    }

    @Test
    void validate_selectWithWhitespace_succeeds() {
        assertDoesNotThrow(() ->
            SqlGuard.validateSelectOnly("\n  SELECT id, name FROM bi_metric\n"));
    }

    @Test
    void validate_selectCaseInsensitive_succeeds() {
        assertDoesNotThrow(() ->
            SqlGuard.validateSelectOnly("select * from bi_metric"));
        assertDoesNotThrow(() ->
            SqlGuard.validateSelectOnly("SeLeCt id From bi_metric"));
    }

    @Test
    void validate_selectWithWhereGroupBy_succeeds() {
        assertDoesNotThrow(() -> SqlGuard.validateSelectOnly(
            "SELECT dept_id, SUM(amount) FROM fin_voucher " +
            "WHERE tenant_id = ? AND period = '2026-09' " +
            "GROUP BY dept_id ORDER BY SUM(amount) DESC"));
    }

    @Test
    void validate_selectWithTrailingSemicolon_succeeds() {
        assertDoesNotThrow(() ->
            SqlGuard.validateSelectOnly("SELECT * FROM bi_metric;"));
    }

    @Test
    void validate_selectWithSelect_succeeds() {
        // SELECT 关键字出现在列名/表名中应允许 (整词边界)
        assertDoesNotThrow(() ->
            SqlGuard.validateSelectOnly("SELECT select_name FROM bi_select_config"));
    }

    // ---------- validateSelectOnly: 非法语句 ----------

    @Test
    void validate_insert_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("INSERT INTO bi_metric VALUES (1, 'x')"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_update_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("UPDATE bi_metric SET name = 'x'"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_delete_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("DELETE FROM bi_metric"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_drop_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("DROP TABLE bi_metric"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_alter_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("ALTER TABLE bi_metric ADD COLUMN x INT"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_truncate_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("TRUNCATE TABLE bi_metric"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_create_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("CREATE TABLE evil (id INT)"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_grant_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("GRANT ALL ON *.* TO 'attacker'"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_revoke_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("REVOKE ALL ON *.* FROM 'user'"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_set_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("SET @x = 1"));
        assertEquals(400, ex.getCode());
    }

    // ---------- validateSelectOnly: 多语句注入 ----------

    @Test
    void validate_multipleStatements_throws400() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("SELECT * FROM bi_metric; DROP TABLE bi_metric"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("multiple SQL"));
    }

    @Test
    void validate_injectInWhere_throws400() {
        // UNION 注入 SELECT 仍允许 (黑名单不含 UNION), 但 INSERT 嵌入会拒
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("SELECT * FROM bi_metric WHERE id = 1; INSERT INTO evil VALUES(1)"));
        assertEquals(400, ex.getCode());
    }

    // ---------- validateSelectOnly: 空白/空 ----------

    @Test
    void validate_empty_throws400() {
        assertThrows(ServiceException.class, () -> SqlGuard.validateSelectOnly(""));
        assertThrows(ServiceException.class, () -> SqlGuard.validateSelectOnly("   "));
        assertThrows(ServiceException.class, () -> SqlGuard.validateSelectOnly(null));
    }

    @Test
    void validate_keywordInColumnName_notFalsePositive() {
        // updated_at 列名不应误伤
        assertDoesNotThrow(() -> SqlGuard.validateSelectOnly(
            "SELECT id, name, updated_at FROM bi_metric WHERE tenant_id = ?"));
    }

    @Test
    void validate_keywordInLiteralString_throws400() {
        // 字符串里出现 update 仍会被黑名单命中 (保守安全)
        ServiceException ex = assertThrows(ServiceException.class, () ->
            SqlGuard.validateSelectOnly("SELECT 'this is an update' FROM bi_metric"));
        assertEquals(400, ex.getCode());
    }

    // ---------- enforceLimit ----------

    @Test
    void enforceLimit_appendsWhenAbsent() {
        String out = SqlGuard.enforceLimit("SELECT * FROM bi_metric", 100);
        assertTrue(out.toUpperCase().contains("LIMIT 100"));
    }

    @Test
    void enforceLimit_doesNotDuplicateWhenPresent() {
        String sql = "SELECT * FROM bi_metric LIMIT 50";
        String out = SqlGuard.enforceLimit(sql, 100);
        // 已有 LIMIT 不再追加
        assertEquals(1, out.toUpperCase().split("LIMIT").length - 1);
    }

    @Test
    void enforceLimit_stripsTrailingSemicolon() {
        String out = SqlGuard.enforceLimit("SELECT * FROM bi_metric;", 10);
        assertTrue(out.toUpperCase().contains("LIMIT 10"));
        assertFalse(out.contains(";"));
    }

    // ---------- injectTenantFilter ----------

    @Test
    void injectTenant_addsWhenAbsent() {
        String sql = "SELECT * FROM bi_metric";
        String out = SqlGuard.injectTenantFilter(sql);
        assertTrue(out.toLowerCase().contains("tenant_id = ?"));
    }

    @Test
    void injectTenant_usesAndWhenWhereExists() {
        String sql = "SELECT * FROM bi_metric WHERE id > 0";
        String out = SqlGuard.injectTenantFilter(sql);
        assertTrue(out.toLowerCase().contains("tenant_id = ?"));
        // 末尾注入 AND tenant_id = ?
        assertTrue(out.toLowerCase().contains("and tenant_id = ?"));
    }

    @Test
    void injectTenant_doesNotDuplicate() {
        String sql = "SELECT * FROM bi_metric WHERE tenant_id = 5";
        String out = SqlGuard.injectTenantFilter(sql);
        // 不重复注入
        int count = (out.toLowerCase().split("tenant_id", -1).length - 1);
        assertEquals(1, count);
    }
}