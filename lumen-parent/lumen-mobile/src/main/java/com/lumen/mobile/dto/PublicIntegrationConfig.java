package com.lumen.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 非管理员可见的协作平台公开字段（安全要求 #12）。
 * 不暴露 appSecretEnc / webhookUrlEnc — 这些只能 admin 通过 getEnabledConfig 拿原文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicIntegrationConfig {
    private Long id;
    private String code;
    private String platform;
    private String appId;
    private String agentId;
    private String redirectUri;
    private Integer enabled;
}
