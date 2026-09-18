package com.lumen.auth.service;

import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserMfa;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.auth.mapper.SysUserMfaMapper;
import com.lumen.common.core.constant.CommonConstants;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.common.security.mfa.TotpGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MfaService}. Pure Mockito — no live DB or Redis.
 *
 * <p>Real {@link TotpGenerator} is exercised end-to-end (HMAC, Base32, time bucket)
 * to catch mismatches between service-side secret encoding and verify math; we
 * mock only the persistence layer.</p>
 */
@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock private SysUserMfaMapper mfaMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;

    @InjectMocks private MfaService mfaService;

    private static final long UID = 42L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void enroll_forNewUser_returnsSecretAndPersistsDisabledRow() {
        SysUser user = new SysUser();
        user.setUserId(UID);
        user.setUserName("alice");
        when(userMapper.findByUserId(UID)).thenReturn(user);
        when(mfaMapper.selectById(UID)).thenReturn(null);

        var resp = mfaService.enroll();

        assertNotNull(resp);
        assertEquals(CommonConstants.SUCCESS_CODE, resp.getCode());
        var result = resp.getData();
        assertNotNull(result);
        assertNotNull(result.secret());
        assertEquals(32, result.secret().length());
        assertTrue(result.otpauthUrl().startsWith("otpauth://totp/"));
        assertTrue(result.otpauthUrl().contains("secret=" + result.secret()));
        assertTrue(result.otpauthUrl().contains("issuer=Lumen"));

        ArgumentCaptor<SysUserMfa> captor = ArgumentCaptor.forClass(SysUserMfa.class);
        verify(mfaMapper).insert(captor.capture());
        SysUserMfa row = captor.getValue();
        assertEquals(UID, row.getUserId());
        assertEquals(result.secret(), row.getSecret());
        assertEquals(0, row.getEnabled());
        verify(mfaMapper, never()).updateById(any());
    }

    @Test
    void enroll_forExistingDisabledUser_regeneratesSecret() {
        SysUser user = new SysUser();
        user.setUserId(UID);
        user.setUserName("alice");

        SysUserMfa existing = new SysUserMfa();
        existing.setUserId(UID);
        existing.setSecret("OLD-SECRET-PLEASE-DISCARD");
        existing.setEnabled(0);
        existing.setEnrolledAt(null);

        when(userMapper.findByUserId(UID)).thenReturn(user);
        when(mfaMapper.selectById(UID)).thenReturn(existing);

        var resp = mfaService.enroll();
        assertEquals(CommonConstants.SUCCESS_CODE, resp.getCode());
        String newSecret = resp.getData().secret();
        assertNotNull(newSecret);
        assertNotEquals("OLD-SECRET-PLEASE-DISCARD", newSecret);

        verify(mfaMapper, never()).insert(any());
        verify(mfaMapper).updateById(existing);
        assertEquals(0, existing.getEnabled());
        assertNull(existing.getEnrolledAt());
        assertNull(existing.getBackupCodes());
    }

    @Test
    void enroll_forExistingEnabledUser_returnsExistingSecretWithoutRotation() {
        SysUser user = new SysUser();
        user.setUserId(UID);
        user.setUserName("alice");

        SysUserMfa existing = new SysUserMfa();
        existing.setUserId(UID);
        existing.setSecret("ACTIVE-SECRET");
        existing.setEnabled(1);

        when(userMapper.findByUserId(UID)).thenReturn(user);
        when(mfaMapper.selectById(UID)).thenReturn(existing);

        var resp = mfaService.enroll();
        // Active enrollment: return the existing secret unchanged (re-enroll / new device).
        // We MUST NOT silently rotate the secret — that would invalidate the user's
        // existing authenticator.
        assertEquals("ACTIVE-SECRET", resp.getData().secret());
        assertEquals(1, existing.getEnabled()); // unchanged
        verify(mfaMapper, never()).updateById(any());
    }

    @Test
    void confirm_withValidCode_marksEnabledAndReturnsBackupCodes() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(0);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        String code = TotpGenerator.currentCode(row.getSecret());

        var resp = mfaService.confirm(code);

        assertEquals(CommonConstants.SUCCESS_CODE, resp.getCode());
        List<String> codes = resp.getData();
        assertNotNull(codes);
        assertEquals(10, codes.size());
        for (String c : codes) {
            assertEquals(8, c.length());
        }
        assertEquals(1, row.getEnabled());
        assertNotNull(row.getEnrolledAt());
        assertNotNull(row.getBackupCodes());
        // Comma-joined equals the codes joined.
        assertEquals(String.join(",", codes), row.getBackupCodes());
        verify(mfaMapper).updateById(row);
    }

    @Test
    void confirm_withInvalidCode_throws401() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(0);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> mfaService.confirm("000000"));
        assertEquals(401, ex.getCode());
        assertEquals(0, row.getEnabled());
        verify(mfaMapper, never()).updateById(any());
    }

    @Test
    void confirm_withoutPriorEnrollment_throws401() {
        when(mfaMapper.selectById(UID)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> mfaService.confirm("123456"));
        assertEquals(401, ex.getCode());
    }

    @Test
    void disable_withValidCode_clearsSecretAndBackupCodes() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(1);
        row.setBackupCodes("AAAA-BBBB-CCCC");
        row.setEnrolledAt(java.time.LocalDateTime.now());
        when(mfaMapper.selectById(UID)).thenReturn(row);

        String code = TotpGenerator.currentCode(row.getSecret());

        var resp = mfaService.disable(code);
        assertEquals(CommonConstants.SUCCESS_CODE, resp.getCode());
        assertEquals(0, row.getEnabled());
        assertNull(row.getSecret());
        assertNull(row.getBackupCodes());
        assertNull(row.getEnrolledAt());
        verify(mfaMapper).updateById(row);
    }

    @Test
    void disable_withInvalidCode_throws401_andLeavesState() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> mfaService.disable("000000"));
        assertEquals(401, ex.getCode());
        assertEquals(1, row.getEnabled()); // unchanged
        assertNotNull(row.getSecret());
        verify(mfaMapper, never()).updateById(any());
    }

    @Test
    void verify_delegatesToTotpGenerator() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        String secret = TotpGenerator.generateSecret();
        row.setSecret(secret);
        row.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        assertTrue(mfaService.verify(UID, TotpGenerator.currentCode(secret)));
        assertFalse(mfaService.verify(UID, "000000"));
        assertFalse(mfaService.verify(UID, null));
        assertFalse(mfaService.verify(null, "123456"));
    }

    @Test
    void verify_forDisabledEnrollment_returnsFalse() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(0);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        // Even with the right code, a not-yet-confirmed enrollment must not pass verify.
        assertFalse(mfaService.verify(UID, TotpGenerator.currentCode(row.getSecret())));
    }

    @Test
    void isEnabled_reflectsEnrollmentState() {
        when(mfaMapper.selectById(UID)).thenReturn(null);
        assertFalse(mfaService.isEnabled(UID));

        SysUserMfa disabled = new SysUserMfa();
        disabled.setUserId(UID);
        disabled.setEnabled(0);
        when(mfaMapper.selectById(UID)).thenReturn(disabled);
        assertFalse(mfaService.isEnabled(UID));

        SysUserMfa enabled = new SysUserMfa();
        enabled.setUserId(UID);
        enabled.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(enabled);
        assertTrue(mfaService.isEnabled(UID));

        assertFalse(mfaService.isEnabled(null));
    }
}