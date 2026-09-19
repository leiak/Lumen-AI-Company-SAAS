package com.lumen.mobile.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.entity.MobPushToken;
import com.lumen.mobile.mapper.PushTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 推送 Token 服务。
 *
 * <p>关键安全设计（安全要求 #3）：</p>
 * <ul>
 *   <li>{@code register} 接收明文 token，但存库时 AES-256-GCM 加密（AES 走 {@link com.lumen.common.crypto.EncryptedStringTypeHandler}）。</li>
 *   <li>同时存 {@code tokenHash = md5(token)} 作为查询索引，避免密文比对无意义。</li>
 *   <li>{@code register} 先 {@code findByPlatformAndToken} 查重（同一 token 重复注册视为刷新设备，update last_active_at）。</li>
 *   <li>同一 user + platform 仅一个 active token（安全要求 #9）：register 前先 deactivate 旧记录。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    public static final int MAX_TOKEN_LENGTH = 512;

    private final PushTokenMapper pushTokenMapper;

    /**
     * 注册推送 token（用户从 App 端调用）。明文 token 内部 AES 加密 + md5 索引后入库。
     *
     * <p>同 (platform, tokenHash) 已存在 → update 元数据 + last_active_at；
     * 否则插入新记录前先 deactivate 该 user+platform 旧 active token（保证一次只有一个 active）。</p>
     */
    @Transactional
    public MobPushToken register(Long userId, String platform, String token,
                                  String deviceId, String appVersion,
                                  String deviceModel, String osVersion) {
        requireUserCtx();
        if (token == null || token.isBlank()) {
            throw new ServiceException(400, "token is required");
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new ServiceException(400, "token too long (max " + MAX_TOKEN_LENGTH + ")");
        }
        UserContext ctx = UserContextHolder.get();

        String hash = md5Hex(token);

        // 同 token 已注册 → 刷新元数据 + 重新激活
        MobPushToken existing = pushTokenMapper.findByPlatformAndToken(platform, hash);
        if (existing != null) {
            existing.setDeviceId(deviceId);
            existing.setAppVersion(appVersion);
            existing.setDeviceModel(deviceModel);
            existing.setOsVersion(osVersion);
            existing.setStatus(STATUS_ACTIVE);
            existing.setLastActiveAt(LocalDateTime.now());
            // 注意: pushTokenEnc 字段已加密且与 token 一一对应，无需重写（若 token 真变化了 hash 也会变）
            pushTokenMapper.updateById(existing);
            log.info("Push token refreshed userId={} platform={} id={}", userId, platform, existing.getId());
            return existing;
        }

        // 同 user+platform 已有 active → deactivate 旧记录（安全要求 #9 一次只能一个 active）
        List<MobPushToken> actives = pushTokenMapper.findActiveByUser(userId);
        for (MobPushToken old : actives) {
            if (platform.equals(old.getPlatform())) {
                old.setStatus(STATUS_INACTIVE);
                pushTokenMapper.updateById(old);
                log.info("Deactivated prior push token id={} userId={} platform={}",
                    old.getId(), userId, platform);
            }
        }

        MobPushToken t = new MobPushToken();
        t.setTenantId(ctx.getTenantId());
        t.setUserId(userId);
        t.setPlatform(platform);
        t.setDeviceId(deviceId);
        t.setPushTokenEnc(token);  // EncryptedStringTypeHandler 自动加密落库
        t.setTokenHash(hash);
        t.setAppVersion(appVersion);
        t.setDeviceModel(deviceModel);
        t.setOsVersion(osVersion);
        t.setStatus(STATUS_ACTIVE);
        t.setLastActiveAt(LocalDateTime.now());
        try {
            pushTokenMapper.insert(t);
        } catch (DuplicateKeyException ex) {
            // UNIQUE(platform, device_id, deleted) 兜底
            throw new ServiceException(409, "Push token already registered for this device", ex);
        }
        log.info("Push token registered userId={} platform={} id={}", userId, platform, t.getId());
        return t;
    }

    /**
     * 注销 token（用户登出或 App 卸载时调用）。
     * 通过 tokenHash 定位；找不到时返回 false（幂等）。
     */
    @Transactional
    public boolean unregister(String platform, String token) {
        requireUserCtx();
        if (token == null || token.isBlank()) return false;
        String hash = md5Hex(token);
        MobPushToken existing = pushTokenMapper.findByPlatformAndToken(platform, hash);
        if (existing == null) return false;
        existing.setStatus(STATUS_INACTIVE);
        pushTokenMapper.updateById(existing);
        log.info("Push token unregistered platform={} id={}", platform, existing.getId());
        return true;
    }

    /**
     * 用户全部 active token — 给 {@link PushService} 用。
     */
    public List<MobPushToken> getActiveTokens(Long userId) {
        return pushTokenMapper.findActiveByUser(userId);
    }

    /**
     * 用户全部 token（含 inactive，用于设备管理界面）。
     */
    public List<MobPushToken> listByUser(Long userId) {
        requireUserCtx();
        return pushTokenMapper.findByUser(userId);
    }

    /**
     * 按 tokenHash 标记 inactive。
     */
    @Transactional
    public boolean deactivate(String platform, String token) {
        requireUserCtx();
        if (token == null || token.isBlank()) return false;
        String hash = md5Hex(token);
        MobPushToken existing = pushTokenMapper.findByPlatformAndToken(platform, hash);
        if (existing == null) return false;
        if (!STATUS_ACTIVE.equals(existing.getStatus())) return true;
        existing.setStatus(STATUS_INACTIVE);
        pushTokenMapper.updateById(existing);
        return true;
    }

    /**
     * md5(token) → 32-char hex。
     */
    public static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private UserContext requireUserCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "Missing user context");
        }
        return ctx;
    }
}
