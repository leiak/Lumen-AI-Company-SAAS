package com.lumen.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.dto.SaveAppVersionRequest;
import com.lumen.mobile.dto.UpdateResponse;
import com.lumen.mobile.entity.MobAppVersion;
import com.lumen.mobile.mapper.AppVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * App 版本服务。
 *
 * <p>核心方法：{@link #checkUpdate}（公开）— 比较客户端 currentVersion 与最新 released，
 * 决定是否提示升级 / 是否强制升级（forceUpdate=1 或 currentVersion < minSupportedVersion）。</p>
 *
 * <p>status 状态机：draft → released → deprecated。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionService {

    public static final String PLATFORM_ANDROID = "android";
    public static final String PLATFORM_IOS = "ios";
    public static final String PLATFORM_HARMONY = "harmony";

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_RELEASED = "released";
    public static final String STATUS_DEPRECATED = "deprecated";

    private final AppVersionMapper versionMapper;

    /**
     * 公开升级检测。platform + currentVersion 由客户端 query 提供，service 不读 ctx。
     */
    public UpdateResponse checkUpdate(String platform, String currentVersion) {
        MobAppVersion current = versionMapper.findByPlatformAndVersion(platform, currentVersion);
        MobAppVersion latest = versionMapper.findLatestReleased(platform);
        if (latest == null) {
            // 平台尚无 released 版本 — 不提示升级
            return new UpdateResponse(false, false, null, null, null, null);
        }
        if (current != null && latest.getId().equals(current.getId())) {
            // 已是最新
            return new UpdateResponse(false, false, latest.getVersion(), latest.getBuildNumber(),
                null, null);
        }

        boolean needsUpdate = !latest.getVersion().equals(currentVersion)
            || (current != null && current.getBuildNumber() != null
                && latest.getBuildNumber() != null
                && current.getBuildNumber() < latest.getBuildNumber());

        // 强制升级条件: latest.forceUpdate=1 或 currentVersion < minSupportedVersion
        // 注意: 即便 current 在 DB 中不存在 (用户版本过老), 也要用客户端上送的 version 比 min_supported_version
        boolean force = latest.getForceUpdate() != null && latest.getForceUpdate() == 1;
        if (!force && latest.getMinSupportedVersion() != null
            && !latest.getMinSupportedVersion().isBlank()) {
            force = compareVersion(currentVersion, latest.getMinSupportedVersion()) < 0;
        }

        return new UpdateResponse(needsUpdate, force, latest.getVersion(),
            latest.getBuildNumber(), latest.getDownloadUrl(), latest.getReleaseNotes());
    }

    /**
     * 平台最新已发布版本 — 用于服务端缓存预热 / 后台报表。
     */
    public MobAppVersion latest(String platform) {
        return versionMapper.findLatestReleased(platform);
    }

    public IPage<MobAppVersion> page(int pageNum, int pageSize, String platform, String status) {
        requireAdminCtx();
        var w = new LambdaQueryWrapper<MobAppVersion>().orderByDesc(MobAppVersion::getId);
        if (platform != null && !platform.isBlank()) w.eq(MobAppVersion::getPlatform, platform);
        if (status != null && !status.isBlank()) w.eq(MobAppVersion::getStatus, status);
        return versionMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public MobAppVersion get(Long id) {
        requireAdminCtx();
        MobAppVersion v = versionMapper.selectById(id);
        if (v == null) throw new ServiceException(404, "App version not found: " + id);
        return v;
    }

    @Transactional
    public MobAppVersion save(SaveAppVersionRequest req) {
        requireAdminCtx();
        validateStatusTransition(null, req.getStatus());
        MobAppVersion v = new MobAppVersion();
        v.setPlatform(req.getPlatform());
        v.setVersion(req.getVersion());
        v.setBuildNumber(req.getBuildNumber());
        v.setForceUpdate(req.getForceUpdate() == null ? 0 : req.getForceUpdate());
        v.setMinSupportedVersion(req.getMinSupportedVersion());
        v.setDownloadUrl(req.getDownloadUrl());
        v.setReleaseNotes(req.getReleaseNotes());
        v.setStatus(req.getStatus() == null ? STATUS_DRAFT : req.getStatus());
        v.setReleasedAt(req.getReleasedAt());
        try {
            versionMapper.insert(v);
        } catch (DuplicateKeyException ex) {
            // UNIQUE(platform, version, deleted) 冲突
            throw new ServiceException(409,
                "App version already exists for platform=" + req.getPlatform()
                    + " version=" + req.getVersion(), ex);
        }
        log.info("App version created id={} platform={} version={}", v.getId(), v.getPlatform(), v.getVersion());
        return v;
    }

    @Transactional
    public MobAppVersion update(Long id, SaveAppVersionRequest req) {
        requireAdminCtx();
        MobAppVersion existing = get(id);
        validateStatusTransition(existing.getStatus(), req.getStatus());
        if (req.getBuildNumber() != null) existing.setBuildNumber(req.getBuildNumber());
        if (req.getForceUpdate() != null) existing.setForceUpdate(req.getForceUpdate());
        if (req.getMinSupportedVersion() != null) existing.setMinSupportedVersion(req.getMinSupportedVersion());
        if (req.getDownloadUrl() != null) existing.setDownloadUrl(req.getDownloadUrl());
        if (req.getReleaseNotes() != null) existing.setReleaseNotes(req.getReleaseNotes());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getReleasedAt() != null) existing.setReleasedAt(req.getReleasedAt());
        versionMapper.updateById(existing);
        return existing;
    }

    /**
     * draft → released 升级。released 自动填 released_at = now。
     */
    @Transactional
    public MobAppVersion release(Long id) {
        requireAdminCtx();
        MobAppVersion v = get(id);
        if (STATUS_RELEASED.equals(v.getStatus())) {
            return v;
        }
        if (STATUS_DEPRECATED.equals(v.getStatus())) {
            throw new ServiceException(409, "Cannot release deprecated version: " + id);
        }
        v.setStatus(STATUS_RELEASED);
        v.setReleasedAt(LocalDateTime.now());
        versionMapper.updateById(v);
        log.info("App version released id={} version={}", id, v.getVersion());
        return v;
    }

    @Transactional
    public void delete(Long id) {
        requireAdminCtx();
        MobAppVersion existing = get(id);
        versionMapper.deleteById(existing.getId());
        log.info("App version deleted id={}", id);
    }

    private void validateStatusTransition(String current, String next) {
        if (next == null) return;
        if (current == null) {
            // 新建
            if (!STATUS_DRAFT.equals(next) && !STATUS_RELEASED.equals(next)) {
                throw new ServiceException(400, "status must be draft or released on create");
            }
            return;
        }
        if (STATUS_DEPRECATED.equals(current) && !STATUS_DEPRECATED.equals(next)) {
            throw new ServiceException(409, "Cannot transition from deprecated");
        }
    }

    /**
     * 简单语义版本比较：a < b 返回 -1，a == b 返回 0，a > b 返回 1。
     * 忽略 -suffix。
     */
    static int compareVersion(String a, String b) {
        if (a == null || b == null) return 0;
        String[] pa = a.split("-")[0].split("\\.");
        String[] pb = b.split("-")[0].split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? parseInt(pa[i]) : 0;
            int nb = i < pb.length ? parseInt(pb[i]) : 0;
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private UserContext requireAdminCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }
}
