package com.lumen.mobile.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.mobile.entity.MobPushToken;
import com.lumen.mobile.mapper.PushTokenMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock private PushTokenMapper mapper;

    @InjectMocks private PushTokenService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private MobPushToken mkToken(Long id, Long userId, String platform, String status) {
        MobPushToken t = new MobPushToken();
        t.setId(id);
        t.setUserId(userId);
        t.setPlatform(platform);
        t.setDeviceId("device-" + id);
        t.setPushTokenEnc("enc:" + id); // 已加密（TypeHandler 在 DB 层）
        t.setTokenHash("hash" + id);
        t.setStatus(status);
        return t;
    }

    // ----- 注册去重 / md5 索引 -----

    @Test
    void md5Hex_isStable() {
        assertEquals(PushTokenService.md5Hex("token-abc"),
            PushTokenService.md5Hex("token-abc"));
        assertNotEquals(PushTokenService.md5Hex("token-abc"),
            PushTokenService.md5Hex("token-xyz"));
        assertEquals(32, PushTokenService.md5Hex("token-abc").length()); // md5 hex
    }

    @Test
    void register_newToken_insertsWithHashAndTenant() {
        String rawToken = "fcm-token-abc";
        when(mapper.findByPlatformAndToken(eq("android"), anyString())).thenReturn(null);
        when(mapper.findActiveByUser(UID)).thenReturn(List.of());
        when(mapper.insert(any(MobPushToken.class))).thenAnswer(inv -> {
            MobPushToken t = inv.getArgument(0);
            t.setId(50L);
            return 1;
        });

        MobPushToken out = service.register(UID, "android", rawToken,
            "device-1", "1.0.0", "Pixel 7", "Android 14");

        assertEquals(50L, out.getId());
        assertEquals(TID, out.getTenantId());
        assertEquals(UID, out.getUserId());
        assertEquals("android", out.getPlatform());
        assertEquals(rawToken, out.getPushTokenEnc()); // service 写明文（TypeHandler 加密落库）
        // tokenHash = md5(token) 索引
        assertEquals(PushTokenService.md5Hex(rawToken), out.getTokenHash());
        assertEquals("active", out.getStatus());
        verify(mapper).insert(any(MobPushToken.class));
    }

    @Test
    void register_sameToken_updatesLastActive() {
        String rawToken = "fcm-token-abc";
        MobPushToken existing = mkToken(7L, UID, "android", "active");
        existing.setPushTokenEnc(rawToken); // 已存在 → 不重置 encrypted value
        when(mapper.findByPlatformAndToken(eq("android"), anyString())).thenReturn(existing);

        MobPushToken out = service.register(UID, "android", rawToken,
            "device-1", "1.0.0", "Pixel 7", "Android 14");

        assertEquals(7L, out.getId());
        assertNotNull(out.getLastActiveAt());
        verify(mapper, never()).insert(any());
        verify(mapper).updateById(existing);
    }

    /**
     * 安全要求 #9: 同 user+platform 只能有一个 active — register 前 deactivate 旧的。
     */
    @Test
    void register_existingActiveSamePlatform_deactivatesOld() {
        String rawToken = "fcm-token-NEW";
        MobPushToken oldActive = mkToken(7L, UID, "android", "active");
        MobPushToken oldIos = mkToken(8L, UID, "ios", "active"); // 不同平台不动

        when(mapper.findByPlatformAndToken(eq("android"), anyString())).thenReturn(null);
        when(mapper.findActiveByUser(UID)).thenReturn(List.of(oldActive, oldIos));
        when(mapper.insert(any(MobPushToken.class))).thenAnswer(inv -> {
            MobPushToken t = inv.getArgument(0);
            t.setId(50L);
            return 1;
        });

        service.register(UID, "android", rawToken, "device-1", null, null, null);

        // 旧 android active → inactive
        assertEquals("inactive", oldActive.getStatus());
        verify(mapper).updateById(oldActive);
        // 旧 ios 不动
        verify(mapper, never()).updateById(oldIos);
    }

    @Test
    void register_emptyToken_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.register(UID, "android", "", "d1", null, null, null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void register_tokenTooLong_throws400() {
        String tooLong = "a".repeat(PushTokenService.MAX_TOKEN_LENGTH + 1);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.register(UID, "android", tooLong, "d1", null, null, null));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("too long"));
    }

    // ----- 注销 / 去活 -----

    @Test
    void unregister_existing_marksInactive() {
        MobPushToken t = mkToken(7L, UID, "android", "active");
        when(mapper.findByPlatformAndToken(eq("android"), eq(PushTokenService.md5Hex("raw"))))
            .thenReturn(t);
        boolean result = service.unregister("android", "raw");
        assertTrue(result);
        assertEquals("inactive", t.getStatus());
        verify(mapper).updateById(t);
    }

    @Test
    void unregister_notFound_returnsFalse() {
        when(mapper.findByPlatformAndToken(eq("android"), anyString())).thenReturn(null);
        assertFalse(service.unregister("android", "raw"));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void unregister_emptyToken_returnsFalse() {
        assertFalse(service.unregister("android", null));
        assertFalse(service.unregister("android", ""));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void deactivate_alreadyInactive_returnsTrueWithoutUpdate() {
        MobPushToken t = mkToken(7L, UID, "android", "inactive");
        when(mapper.findByPlatformAndToken(eq("android"), anyString())).thenReturn(t);
        assertTrue(service.deactivate("android", "raw"));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void register_noUserContext_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.register(UID, "android", "token", "d1", null, null, null));
        assertEquals(401, ex.getCode());
    }

    // ----- tenant 隔离（service 在 register 时 set tenant） -----

    @Test
    void register_setsTenantFromContext() {
        when(mapper.findByPlatformAndToken(anyString(), anyString())).thenReturn(null);
        when(mapper.findActiveByUser(anyLong())).thenReturn(List.of());
        when(mapper.insert(any(MobPushToken.class))).thenAnswer(inv -> {
            MobPushToken t = inv.getArgument(0);
            t.setId(99L);
            return 1;
        });
        ArgumentCaptor<MobPushToken> cap = ArgumentCaptor.forClass(MobPushToken.class);

        service.register(UID, "android", "tok", "d", null, null, null);

        verify(mapper).insert(cap.capture());
        MobPushToken saved = cap.getValue();
        assertEquals(TID, saved.getTenantId());
        assertEquals(UID, saved.getUserId());
        assertNotNull(saved.getTokenHash());
        assertNotEquals("tok", saved.getTokenHash()); // md5 ≠ plaintext
    }
}
