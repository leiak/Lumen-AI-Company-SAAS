package com.lumen.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.dto.PublicIntegrationConfig;
import com.lumen.mobile.dto.SaveIntegrationConfigRequest;
import com.lumen.mobile.entity.IntegrationAppConfig;
import com.lumen.mobile.mapper.IntegrationAppConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 协作平台应用配置服务。
 *
 * <p>安全要求 #4：appSecret + webhookUrl 字段级 AES-GCM 加密。
 * 安全要求 #12：secret 仅 admin 可见；普通用户只暴露 public 字段。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationAppConfigService {

    private static final String PLATFORM_DINGTALK = "dingtalk";
    private static final String PLATFORM_WECHATWORK = "wechatwork";
    private static final String PLATFORM_FEISHU = "feishu";

    private final IntegrationAppConfigMapper configMapper;

    /**
     * Admin only — 返回包含加密字段的完整对象（service 自身不自动解密，调用方按需调 cipher）。
     */
    public IntegrationAppConfig getForAdmin(Long id) {
        requireAdminCtx();
        IntegrationAppConfig c = configMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Integration config not found: " + id);
        return c;
    }

    /**
     * Admin only — 按 code 查（含加密字段）。
     */
    public IntegrationAppConfig getByCodeForAdmin(String code) {
        requireAdminCtx();
        IntegrationAppConfig c = configMapper.findByCode(code);
        if (c == null) throw new ServiceException(404, "Integration config not found: " + code);
        return c;
    }

    /**
     * Admin only — 按 platform 查所有（含加密字段）。
     */
    public List<IntegrationAppConfig> findByPlatformForAdmin(String platform) {
        requireAdminCtx();
        return configMapper.findByPlatform(platform);
    }

    /**
     * Admin only — 列所有启用配置（含加密字段），用于集成运行时实际调用平台 API。
     */
    public List<IntegrationAppConfig> findEnabledForAdmin() {
        requireAdminCtx();
        return configMapper.findEnabled();
    }

    /**
     * 普通用户 — 仅返回公开字段（id/code/platform/appId/agentId/redirectUri/enabled），
     * 不暴露加密字段（安全要求 #12）。
     */
    public List<PublicIntegrationConfig> findEnabledPublic() {
        requireUserCtx();
        List<IntegrationAppConfig> all = configMapper.findEnabled();
        List<PublicIntegrationConfig> out = new ArrayList<>(all.size());
        for (IntegrationAppConfig c : all) {
            out.add(new PublicIntegrationConfig(
                c.getId(), c.getCode(), c.getPlatform(), c.getAppId(),
                c.getAgentId(), c.getRedirectUri(), c.getEnabled()));
        }
        return out;
    }

    /**
     * 单平台公开配置（前端 OAuth 跳转 URL 拼接需要 redirect_uri）。
     */
    public PublicIntegrationConfig getPublicForCallback(String platform) {
        requireUserCtx();
        List<IntegrationAppConfig> all = configMapper.findByPlatform(platform);
        for (IntegrationAppConfig c : all) {
            if (c.getEnabled() != null && c.getEnabled() == 1) {
                return new PublicIntegrationConfig(
                    c.getId(), c.getCode(), c.getPlatform(), c.getAppId(),
                    c.getAgentId(), c.getRedirectUri(), c.getEnabled());
            }
        }
        throw new ServiceException(404, "No enabled integration config for platform=" + platform);
    }

    public IPage<IntegrationAppConfig> page(int pageNum, int pageSize, String platform) {
        requireAdminCtx();
        var w = new LambdaQueryWrapper<IntegrationAppConfig>().orderByDesc(IntegrationAppConfig::getId);
        if (platform != null && !platform.isBlank()) w.eq(IntegrationAppConfig::getPlatform, platform);
        return configMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    @Transactional
    public IntegrationAppConfig save(SaveIntegrationConfigRequest req) {
        requireAdminCtx();
        validatePlatform(req.getPlatform());

        IntegrationAppConfig existing = req.getId() != null
            ? configMapper.selectById(req.getId())
            : configMapper.findByCode(req.getCode());

        IntegrationAppConfig c = existing != null ? existing : new IntegrationAppConfig();
        if (existing == null) {
            c.setCode(req.getCode());
        }
        c.setPlatform(req.getPlatform());
        if (req.getAppId() != null) c.setAppId(req.getAppId());
        if (req.getAppSecret() != null && !req.getAppSecret().isBlank()) {
            c.setAppSecretEnc(req.getAppSecret()); // EncryptedStringTypeHandler 加密
        }
        if (req.getAgentId() != null) c.setAgentId(req.getAgentId());
        if (req.getRedirectUri() != null) c.setRedirectUri(req.getRedirectUri());
        if (req.getWebhookUrl() != null && !req.getWebhookUrl().isBlank()) {
            c.setWebhookUrlEnc(req.getWebhookUrl());
        }
        c.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        if (req.getConfig() != null) c.setConfig(req.getConfig());

        try {
            if (existing == null) {
                configMapper.insert(c);
            } else {
                configMapper.updateById(c);
            }
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Integration config code already exists: " + req.getCode(), ex);
        }
        log.info("Integration config saved id={} code={} platform={}",
            c.getId(), c.getCode(), c.getPlatform());
        return c;
    }

    @Transactional
    public void delete(Long id) {
        requireAdminCtx();
        IntegrationAppConfig existing = configMapper.selectById(id);
        if (existing == null) throw new ServiceException(404, "Integration config not found: " + id);
        configMapper.deleteById(id);
        log.info("Integration config deleted id={}", id);
    }

    private void validatePlatform(String platform) {
        if (platform == null
            || (!PLATFORM_DINGTALK.equals(platform)
                && !PLATFORM_WECHATWORK.equals(platform)
                && !PLATFORM_FEISHU.equals(platform))) {
            throw new ServiceException(400,
                "platform must be one of: dingtalk / wechatwork / feishu");
        }
    }

    private UserContext requireAdminCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    private UserContext requireUserCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ServiceException(401, "Missing user context");
        }
        return ctx;
    }
}
