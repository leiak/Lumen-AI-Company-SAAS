package com.lumen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * {@code POST /sso/exchange} 请求体：下游应用把票据交给认证服务换取 JWT。
 */
@Data
public class SsoExchangeRequest {

    /** 已签发的票据字符串。 */
    @NotBlank
    @Size(max = 256)
    private String ticket;

    /** 调用方应用标识，必须与签发时一致。 */
    @NotBlank
    @Size(max = 64)
    private String appId;
}