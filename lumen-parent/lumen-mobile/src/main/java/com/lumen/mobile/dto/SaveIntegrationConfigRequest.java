package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

/**
 * 保存协作平台应用配置请求。
 *
 * <p>{@code appSecret} 接收明文，service 层 AES-GCM 加密后入库。
 * {@code webhookUrl} 同样接收明文，加密后存 {@code webhookUrlEnc}。</p>
 *
 * <p>只有 super_admin / integration_admin / admin 可调（@PreAuthorize）。</p>
 */
@Data
public class SaveIntegrationConfigRequest {

    private Long id;

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "^(dingtalk|wechatwork|feishu)$",
        message = "platform must be one of: dingtalk / wechatwork / feishu")
    private String platform;

    private String appId;

    /** 明文，service 加密。 */
    private String appSecret;

    private String agentId;

    private String redirectUri;

    /** 明文 Webhook URL，service 加密。 */
    private String webhookUrl;

    /** 0=禁用 1=启用；默认 1。 */
    private Integer enabled;

    /** 扩展 JSON 配置。 */
    private Map<String, Object> config;
}
