package com.lumen.auth.service;

import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserMfa;
import com.lumen.auth.entity.SysUserMfaBackupCode;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.auth.mapper.SysUserMfaBackupCodeMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private SysUserMfaBackupCodeMapper backupCodeMapper;
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

    /**
     * C2: two concurrent enroll() calls may both observe no existing row. The loser of
     * the insert race must not propagate a 500; it must fall through to the existing
     * row's updateById path.
     */
    @Test
    void enroll_raceOnDuplicateKey_fallsThroughToUpdate() {
        SysUser user = new SysUser();
        user.setUserId(UID);
        user.setUserName("alice");

        SysUserMfa afterRace = new SysUserMfa();
        afterRace.setUserId(UID);
        afterRace.setSecret("LOSER-SECRET-IGNORED");
        afterRace.setEnabled(0);

        when(userMapper.findByUserId(UID)).thenReturn(user);
        when(mfaMapper.selectById(UID))
            .thenReturn(null)              // first call — race window
            .thenReturn(afterRace);        // second call — re-read after DuplicateKeyException
        doThrow(new DuplicateKeyException("PK collision"))
            .when(mfaMapper).insert(any(SysUserMfa.class));

        var resp = mfaService.enroll();
        assertEquals(CommonConstants.SUCCESS_CODE, resp.getCode());
        // Secret should be a fresh one (rotation path), NOT the LOSER-SECRET-IGNORED
        // stale value seen after the race recovery.
        assertNotEquals("LOSER-SECRET-IGNORED", resp.getData().secret());
        verify(mfaMapper).insert(any(SysUserMfa.class));
        verify(mfaMapper).updateById(afterRace);
    }

    @Test
    void confirm_withValidCode_marksEnabledInsertsHashedBackupCodesAndReturnsPlaintext() {
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

        // Legacy plaintext column must be cleared (we now persist bcrypt hashes only).
        assertNull(row.getBackupCodes());

        verify(mfaMapper).updateById(row);

        // Stale backup codes from a previous enrollment must be purged before inserts.
        verify(backupCodeMapper).deleteAllByUserId(UID);

        // 10 hash rows must be inserted, one per code. Capture and verify hashes are bcrypt
        // and not equal to the plaintexts.
        ArgumentCaptor<SysUserMfaBackupCode> bcCaptor =
            ArgumentCaptor.forClass(SysUserMfaBackupCode.class);
        verify(backupCodeMapper, times(10)).insert(bcCaptor.capture());
        List<SysUserMfaBackupCode> rows = bcCaptor.getAllValues();
        for (int i = 0; i < rows.size(); i++) {
            SysUserMfaBackupCode r = rows.get(i);
            assertEquals(UID, r.getUserId());
            String hash = r.getCodeHash();
            assertNotNull(hash);
            assertNotEquals(codes.get(i), hash);
            assertTrue(hash.startsWith("$2"), "bcrypt hash must start with $2: " + hash);
            assertTrue(hash.length() >= 59 && hash.length() <= 72,
                "bcrypt hash length out of range: " + hash.length());
        }
        // Each hash should be unique (bcrypt salt is random).
        assertEquals(10, rows.stream().map(SysUserMfaBackupCode::getCodeHash).distinct().count());
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
        verify(backupCodeMapper, never()).insert(any(SysUserMfaBackupCode.class));
    }

    @Test
    void confirm_withoutPriorEnrollment_throws401() {
        when(mfaMapper.selectById(UID)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> mfaService.confirm("123456"));
        assertEquals(401, ex.getCode());
    }

    @Test
    void disable_withValidCode_clearsSecretAndDeletesUnusedBackupCodes() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(1);
        row.setBackupCodes(null);
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
        // Stale unused backup codes must be dropped so they cannot be replayed
        // after a fresh re-enrollment.
        verify(backupCodeMapper).deleteAllUnusedByUserId(UID);
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
        verify(backupCodeMapper, never()).deleteAllUnusedByUserId(anyLong());
    }

    @Test
    void verify_totpSucceeds_neverTouchesBackupCodes() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        String secret = TotpGenerator.generateSecret();
        row.setSecret(secret);
        row.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        assertTrue(mfaService.verify(UID, TotpGenerator.currentCode(secret)));
        verifyNoInteractions(backupCodeMapper);
    }

    @Test
    void verify_totpFailsAndCodeWrongLength_doesNotQueryBackupCodes() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        assertFalse(mfaService.verify(UID, "0000000")); // 7 chars — not a valid TOTP, not 8-char backup
        verifyNoInteractions(backupCodeMapper);
    }

    @Test
    void verify_backupCodeMatch_consumesCodeAndReturnsTrue() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder enc =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        SysUserMfaBackupCode stored = new SysUserMfaBackupCode();
        stored.setId(99L);
        stored.setUserId(UID);
        String plaintext = "AB23CD45";
        stored.setCodeHash(enc.encode(plaintext));
        when(backupCodeMapper.selectUnusedByUserId(UID))
            .thenReturn(new ArrayList<>(Collections.singletonList(stored)));
        when(backupCodeMapper.markUsed(eq(99L), eq(UID), isNull())).thenReturn(1);

        assertTrue(mfaService.verify(UID, plaintext));

        // Consumption MUST be a single conditional UPDATE — id + user + used_at IS NULL.
        verify(backupCodeMapper).markUsed(99L, UID, null);
    }

    @Test
    void verify_backupCodeMatch_butLostRace_returnsFalse() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder enc =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        SysUserMfaBackupCode stored = new SysUserMfaBackupCode();
        stored.setId(99L);
        stored.setUserId(UID);
        stored.setCodeHash(enc.encode("AB23CD45"));
        when(backupCodeMapper.selectUnusedByUserId(UID))
            .thenReturn(new ArrayList<>(Collections.singletonList(stored)));
        // The atomic UPDATE returns 0 — another concurrent request beat us to it.
        when(backupCodeMapper.markUsed(eq(99L), eq(UID), isNull())).thenReturn(0);

        assertFalse(mfaService.verify(UID, "AB23CD45"));
    }

    @Test
    void verify_backupCodeHashDoesNotMatch_returnsFalse() {
        SysUserMfa row = new SysUserMfa();
        row.setUserId(UID);
        row.setSecret(TotpGenerator.generateSecret());
        row.setEnabled(1);
        when(mfaMapper.selectById(UID)).thenReturn(row);

        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder enc =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        SysUserMfaBackupCode stored = new SysUserMfaBackupCode();
        stored.setId(99L);
        stored.setUserId(UID);
        stored.setCodeHash(enc.encode("REALCODE"));
        when(backupCodeMapper.selectUnusedByUserId(UID))
            .thenReturn(new ArrayList<>(Collections.singletonList(stored)));

        assertFalse(mfaService.verify(UID, "WRONGCD1"));
        verify(backupCodeMapper, never()).markUsed(anyLong(), anyLong(), any());
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
        verifyNoInteractions(backupCodeMapper);
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