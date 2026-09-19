package com.lumen.bi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Ad-hoc 查询请求。安全要求 #3-#5:
 * - SQL 必须 SELECT 开头 (case-insensitive)
 * - 禁止 INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/REVOKE
 * - 自动追加 LIMIT 1000
 */
@Data
public class AdHocQueryRequest {

    @NotBlank(message = "sql is required")
    private String sql;

    /** 参数化查询参数 (PreparedStatement `?` 占位符值) */
    private Map<String, Object> params;
}