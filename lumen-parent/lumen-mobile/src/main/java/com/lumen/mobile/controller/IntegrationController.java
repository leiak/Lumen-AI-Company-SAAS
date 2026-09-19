package com.lumen.mobile.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.mobile.dto.PublicIntegrationConfig;
import com.lumen.mobile.dto.SaveIntegrationConfigRequest;
import com.lumen.mobile.entity.IntegrationAppConfig;
import com.lumen.mobile.entity.IntegrationEventLog;
import com.lumen.mobile.service.IntegrationAppConfigService;
import com.lumen.mobile.service.IntegrationEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 协作平台集成管理 + 回调入口。
 *
 * <p>安全要求 #5：所有 callback 必须先验签（TODO P5 真实签名校验，本地 stub — 直接放行）。
 * 安全要求 #6：callback 防重放 — {@code sourceId + eventType} UNIQUE 索引在 DB 层兜底。
 * 安全要求 #12：admin 拿到 secret 原文；普通用户只暴露公开字段。</p>
 */
@Slf4j
@RestController
@RequestMapping("/mobile/admin/integration")
@RequiredArgsConstructor
@Validated
public class IntegrationController {

    private final IntegrationAppConfigService configService;
    private final IntegrationEventService eventService;

    // ----- Admin 配置管理 -----

    @GetMapping("/{platform}/config")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<List<IntegrationAppConfig>> listByPlatform(@PathVariable String platform) {
        return R.ok(configService.findByPlatformForAdmin(platform));
    }

    @PostMapping("/config/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<IntegrationAppConfig> save(@RequestBody @Valid SaveIntegrationConfigRequest req) {
        return R.ok(configService.save(req));
    }

    @DeleteMapping("/config/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return R.ok();
    }

    @GetMapping("/config/enabled")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<List<IntegrationAppConfig>> enabled() {
        return R.ok(configService.findEnabledForAdmin());
    }

    @GetMapping("/config/page")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<IPage<IntegrationAppConfig>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String platform) {
        return R.ok(configService.page(pageNum, pageSize, platform));
    }

    // ----- 事件日志 -----

    @GetMapping("/event-log/page")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<IPage<IntegrationEventLog>> eventPage(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String eventType) {
        return R.ok(eventService.page(pageNum, pageSize, platform, eventType));
    }

    @PostMapping("/event-log/{id}/process")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<IntegrationEventLog> processEvent(@PathVariable Long id,
                                                @RequestParam boolean success,
                                                @RequestParam(required = false) String errorMessage) {
        return R.ok(eventService.processEvent(id, success, errorMessage));
    }

    @PostMapping("/event-log/retry-failed")
    @PreAuthorize("hasAnyRole('super_admin','admin','integration_admin')")
    public R<List<Long>> retryFailed(@RequestParam(defaultValue = "3") @Min(1) @Max(10) int maxRetry) {
        return R.ok(eventService.retryFailed(maxRetry));
    }

    // ----- 平台回调入口（安全要求 #5 验签 stub + 安全要求 #6 防重放） -----

    /**
     * 钉钉 SSO / 审批回调。安全要求 #5：验签 stub；安全要求 #6：UNIQUE 防重放。
     */
    @PostMapping("/dingtalk/callback")
    public R<IntegrationEventLog> dingtalkCallback(
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        verifySignatureStub("dingtalk", signature, timestamp, nonce, body);
        return R.ok(processCallback("dingtalk", body));
    }

    /**
     * 企微事件回调。
     */
    @PostMapping("/wechatwork/callback")
    public R<IntegrationEventLog> wechatworkCallback(
            @RequestParam(required = false) String msg_signature,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        verifySignatureStub("wechatwork", msg_signature, null, null, body);
        return R.ok(processCallback("wechatwork", body));
    }

    /**
     * 飞书事件回调。
     */
    @PostMapping("/feishu/callback")
    public R<IntegrationEventLog> feishuCallback(
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestParam(required = false) String nonce,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        verifySignatureStub("feishu", signature, timestamp, nonce, body);
        return R.ok(processCallback("feishu", body));
    }

    /**
     * 钉钉 / 企微 / 飞书 公开回调配置 — 返回 redirect_uri / agent_id（OAuth 跳转用）。
     * 安全要求 #12：不暴露 secret。
     */
    @GetMapping("/{platform}/public")
    public R<PublicIntegrationConfig> publicConfig(@PathVariable String platform) {
        return R.ok(configService.getPublicForCallback(platform));
    }

    @GetMapping("/public-list")
    public R<List<PublicIntegrationConfig>> publicList() {
        return R.ok(configService.findEnabledPublic());
    }

    // ---- helpers ----

    private IntegrationEventLog processCallback(String platform, java.util.Map<String, Object> body) {
        if (body == null) body = new java.util.HashMap<>();
        String eventType = (String) body.getOrDefault("EventType",
            body.getOrDefault("event_type", body.getOrDefault("type", "unknown")));
        String sourceId = (String) body.getOrDefault("EventId",
            body.getOrDefault("event_id", body.getOrDefault("sourceId", null)));
        return eventService.receiveEvent(platform, eventType, sourceId, body);
    }

    /**
     * 签名校验 stub — TODO P5 真实签名校验（钉钉：HMAC-SHA256；企微：AES；飞书：HMAC-SHA256）。
     * 当前阶段直接放行，但保留入口以便 P5 替换。
     */
    private void verifySignatureStub(String platform, String signature, String timestamp,
                                      String nonce, java.util.Map<String, Object> body) {
        // TODO P5 真实签名校验 — 当前不强制（开发期可能没有真实签名）
        if (signature == null) {
            log.debug("[{}] callback missing signature (dev stub); accepting anyway", platform);
        }
    }
}
