package com.lumen.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.dto.SaveAppVersionRequest;
import com.lumen.mobile.entity.MobAppVersion;
import com.lumen.mobile.mapper.AppVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

    @Mock private AppVersionMapper mapper;

    @InjectMocks private AppVersionService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private MobAppVersion released(String platform, String version, int build, int force) {
        MobAppVersion v = new MobAppVersion();
        v.setId(11L);
        v.setPlatform(platform);
        v.setVersion(version);
        v.setBuildNumber(build);
        v.setForceUpdate(force);
        v.setMinSupportedVersion("1.0.0");
        v.setDownloadUrl("https://example.com/" + platform + "-v" + version + ".apk");
        v.setReleaseNotes("release");
        v.setStatus(AppVersionService.STATUS_RELEASED);
        return v;
    }

    private SaveAppVersionRequest req(String platform, String version) {
        SaveAppVersionRequest r = new SaveAppVersionRequest();
        r.setPlatform(platform);
        r.setVersion(version);
        r.setBuildNumber(100);
        r.setForceUpdate(0);
        r.setMinSupportedVersion("1.0.0");
        r.setDownloadUrl("https://example.com/app.apk");
        r.setReleaseNotes("first");
        r.setStatus(AppVersionService.STATUS_DRAFT);
        return r;
    }

    // ----- checkUpdate 强制 / 非强制判定 -----

    @Test
    void checkUpdate_noReleased_returnsNoUpdate() {
        when(mapper.findLatestReleased("android")).thenReturn(null);
        var resp = service.checkUpdate("android", "1.0.0");
        assertFalse(resp.isUpdateAvailable());
        assertFalse(resp.isForceUpdate());
    }

    @Test
    void checkUpdate_currentIsLatest_returnsNoUpdate() {
        MobAppVersion latest = released("android", "1.0.0", 100, 0);
        when(mapper.findLatestReleased("android")).thenReturn(latest);
        when(mapper.findByPlatformAndVersion("android", "1.0.0")).thenReturn(latest);
        var resp = service.checkUpdate("android", "1.0.0");
        assertFalse(resp.isUpdateAvailable());
        assertFalse(resp.isForceUpdate());
    }

    @Test
    void checkUpdate_olderVersion_forceUpdateFlag_respectsForce() {
        MobAppVersion latest = released("android", "2.0.0", 200, 1);
        when(mapper.findLatestReleased("android")).thenReturn(latest);
        when(mapper.findByPlatformAndVersion("android", "1.0.0")).thenReturn(null);
        var resp = service.checkUpdate("android", "1.0.0");
        assertTrue(resp.isUpdateAvailable());
        assertTrue(resp.isForceUpdate());
    }

    @Test
    void checkUpdate_olderVersion_minSupportedVersion_triggersForce() {
        // latest.forceUpdate=0 但 minSupportedVersion=2.0.0 → currentVersion=1.0.0 触发强制升级
        MobAppVersion latest = released("android", "2.0.0", 200, 0);
        latest.setMinSupportedVersion("2.0.0"); // 必须 ≥ 2.0.0
        when(mapper.findLatestReleased("android")).thenReturn(latest);
        when(mapper.findByPlatformAndVersion("android", "1.0.0")).thenReturn(null);
        var resp = service.checkUpdate("android", "1.0.0");
        assertTrue(resp.isUpdateAvailable());
        assertTrue(resp.isForceUpdate());
    }

    @Test
    void checkUpdate_olderVersion_noForce_returnsNonForce() {
        MobAppVersion latest = released("android", "1.1.0", 110, 0);
        latest.setMinSupportedVersion("1.0.0"); // current=1.0.0 不低于
        when(mapper.findLatestReleased("android")).thenReturn(latest);
        when(mapper.findByPlatformAndVersion("android", "1.0.0")).thenReturn(null);
        var resp = service.checkUpdate("android", "1.0.0");
        assertTrue(resp.isUpdateAvailable());
        assertFalse(resp.isForceUpdate());
        assertEquals("1.1.0", resp.getLatestVersion());
        assertEquals("https://example.com/android-v1.1.0.apk", resp.getDownloadUrl());
    }

    // ----- save / release / status 状态机 -----

    @Test
    void save_insertsDraftWithDefaults() {
        when(mapper.insert(any(MobAppVersion.class))).thenAnswer(inv -> {
            MobAppVersion v = inv.getArgument(0);
            v.setId(33L);
            return 1;
        });
        MobAppVersion saved = service.save(req("android", "1.2.0"));
        assertEquals(33L, saved.getId());
        assertEquals(AppVersionService.STATUS_DRAFT, saved.getStatus());
        assertEquals(0, saved.getForceUpdate());
    }

    @Test
    void save_invalidStatus_throws400() {
        SaveAppVersionRequest r = req("android", "1.2.0");
        r.setStatus("deprecated");
        // service 只允许 draft/released on create
        ServiceException ex = assertThrows(ServiceException.class, () -> service.save(r));
        assertEquals(400, ex.getCode());
    }

    @Test
    void release_deprecated_throws409() {
        MobAppVersion v = released("android", "1.0.0", 100, 0);
        v.setStatus(AppVersionService.STATUS_DEPRECATED);
        when(mapper.selectById(11L)).thenReturn(v);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.release(11L));
        assertEquals(409, ex.getCode());
    }

    @Test
    void release_draft_setsReleasedAndTimestamp() {
        MobAppVersion v = released("android", "1.0.0", 100, 0);
        v.setStatus(AppVersionService.STATUS_DRAFT);
        when(mapper.selectById(11L)).thenReturn(v);
        MobAppVersion out = service.release(11L);
        assertEquals(AppVersionService.STATUS_RELEASED, out.getStatus());
        assertNotNull(out.getReleasedAt());
        verify(mapper).updateById(v);
    }

    @Test
    void release_alreadyReleased_idempotent() {
        MobAppVersion v = released("android", "1.0.0", 100, 0);
        // status = RELEASED
        when(mapper.selectById(11L)).thenReturn(v);
        MobAppVersion out = service.release(11L);
        assertEquals(AppVersionService.STATUS_RELEASED, out.getStatus());
        verify(mapper, never()).updateById(any());
    }

    @Test
    void save_noTenant_throws401() {
        UserContextHolder.clear();
        UserContextHolder.set(UserContext.builder().userId(UID).build());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.save(req("android", "1.2.0")));
        assertEquals(401, ex.getCode());
    }

    @Test
    void save_duplicatePlatformVersion_throws409() {
        org.springframework.dao.DuplicateKeyException dup =
            new org.springframework.dao.DuplicateKeyException("uk_app_version_platform_version");
        when(mapper.insert(any(MobAppVersion.class))).thenThrow(dup);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.save(req("android", "1.2.0")));
        assertEquals(409, ex.getCode());
    }

    // ----- version 比较 -----

    @Test
    void compareVersion_basicCases() {
        assertTrue(AppVersionService.compareVersion("1.0.0", "1.0.1") < 0);
        assertTrue(AppVersionService.compareVersion("1.0.1", "1.0.0") > 0);
        assertEquals(0, AppVersionService.compareVersion("1.0.0", "1.0.0"));
        assertTrue(AppVersionService.compareVersion("2.0.0", "10.0.0") < 0); // 段位比较
    }

    @Test
    void compareVersion_ignoresSuffix() {
        assertEquals(0, AppVersionService.compareVersion("1.0.0-beta", "1.0.0"));
        assertTrue(AppVersionService.compareVersion("1.0.0-beta", "1.0.1") < 0);
    }
}
