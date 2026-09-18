package com.lumen.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * {@code POST /sso/issue} 请求体。
 * <p>
 * 注意：{@code userId}/{@code tenantId} 故意不在此处——身份必须来自当前已认证的
 * 会话上下文（{@code UserContextHolder}），绝不允许 body 覆盖，避免越权签发。
 * </p>
 */
@Data
public class SsoIssueRequest {

    /** 目标应用标识；为空时回退到 {@code TicketConstants.DEFAULT_APP_ID}。 */
    @Size(max = 64)
    private String appId;
}