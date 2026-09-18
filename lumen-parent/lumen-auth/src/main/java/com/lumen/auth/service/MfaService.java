package com.lumen.auth.service;

import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserMfa;
import com.lumen.auth.entity.SysUserMfaBackupCode;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.auth.mapper.SysUserMfaBackupCodeMapper;
import com.lumen.auth.mapper.SysUserMfaMapper;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.common.security.mfa.TotpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TOTP MFA enrollment + step-up verification service.
 *
 * <p>Enrollment is global (one row per user across all tenants). The verification step
 * is invoked from {@code LoginService} after password verification when MFA is enrolled
 * or required, and from {@code AuthController#verifyMfa} to complete the step-up.</p>
 *
 * <p>Backup codes: 10 random alphanumeric strings, ambiguous chars excluded
 * (0/O, 1/I/L). The plaintext is returned to the user once at {@link #confirm(String)};
 * only bcrypt hashes are persisted in {@code sys_user_mfa_backup_code}. Each hash is
 * one row, redeemed at most once via {@link #verify(Long, String)} which marks
 * {@code used_at=NOW()} atomically. (Re-issuing individual backup codes is out of
 * scope for P2.)</p>
 *
 * <p>All persistence calls go through mapper default methods backed by explicit
 * {@code @Select}/{@code @Update}/{@code @Delete} SQL — never inline
 * {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper}. This avoids the MyBatis-Plus
 * lambda-cache lookup (which requires Spring context initialisation) and keeps
 * the service pure-unit-testable with plain Mockito stubs.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    /** Issuer label in the otpauth URI — kept short for QR code density. */
    public static final String ISSUER = "Lumen";

    /** Number of backup codes generated on confirm. */
    private static final int BACKUP_CODE_COUNT = 10;
    /** Each backup code is 8 characters from the unambiguous alphabet (32^8 ≈ 1.1e12). */
    private static final int BACKUP_CODE_LENGTH = 8;
    /** Alphanumeric minus ambiguous chars (0, O, 1, I, L). 26 letters + 7 digits = 32. */
    private static final char[] BACKUP_ALPHABET = (
        "ABCDEFGHJKMNPQRSTUVWXYZ"
        + "23456789"
    ).toCharArray();

    private static final SecureRandom RNG = new SecureRandom();

    private final SysUserMfaMapper mfaMapper;
    private final SysUserMfaBackupCodeMapper backupCodeMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate; // reserved for future rate-limit/cache use
    /** bcrypt at default cost (10). Codes are short (8 chars) but slow hash protects brute force. */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Begin (or resume) MFA enrollment for the calling user.
     *
     * <ul>
     *   <li>No existing row → create with fresh secret, enabled=0.</li>
     *   <li>Existing row but enabled=0 → regenerate secret (user lost authenticator;
     *       the previous secret is invalidated so a stolen QR is no longer usable).</li>
     *   <li>Existing row and enabled=1 → return existing secret unchanged (re-enroll
     *       path: user wants to add the same authenticator to a new device without
     *       disturbing the existing one). We do NOT silently rotate the secret on an
     *       already-active enrollment — see self-review checklist.</li>
     * </ul>
     *
     * <p>Race condition: two concurrent enrolls may both observe {@code existing == null}
     * and both attempt {@code insert}, causing the second to fail with a PK violation. We
     * catch {@link DuplicateKeyException} and fall through to {@code updateById} — the user
     * then sees the rotation path, which is acceptable behaviour.</p>
     */
    public R<EnrollResult> enroll() {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new ServiceException(401, "Not authenticated");
        }
        SysUser user = userMapper.findByUserId(userId);
        if (user == null) {
            throw new ServiceException(401, "User not found");
        }

        SysUserMfa existing = mfaMapper.selectById(userId);
        if (existing == null) {
            // First-time enrollment: insert a fresh disabled row.
            String secret = TotpGenerator.generateSecret();
            SysUserMfa row = new SysUserMfa();
            row.setUserId(userId);
            row.setSecret(secret);
            row.setEnabled(0);
            row.setUpdateBy(userId);
            try {
                mfaMapper.insert(row);
                // Build the response with the freshly generated secret. Re-read user for
                // the otpauth label (we already validated existence above; userName may
                // have changed between read and insert in theory).
                SysUser refreshed = userMapper.findByUserId(userId);
                String userName = (refreshed != null && refreshed.getUserName() != null)
                    ? refreshed.getUserName() : String.valueOf(userId);
                log.info("MFA enroll initiated: userId={}, active=false", userId);
                return R.ok(new EnrollResult(secret, buildOtpauthUrl(userName, secret)));
            } catch (DuplicateKeyException race) {
                // Lost the race — another request inserted between selectById and insert.
                // Refresh and fall through to the disabled/enabled branches.
                log.warn("MFA enroll race detected for userId={}, falling through to update", userId);
                existing = mfaMapper.selectById(userId);
                if (existing == null) {
                    // Truly unexpected — rethrow as 500 so we notice in monitoring.
                    throw race;
                }
            }
        }
        return rotateOrReturn(userId, existing);
    }

    /**
     * Continuation of {@link #enroll()} for the "existing row" branch.
     *
     * <ul>
     *   <li>enabled=0 → rotate the secret and reset state (user lost authenticator).</li>
     *   <li>enabled=1 → return the existing secret untouched (re-enroll / new device).</li>
     * </ul>
     */
    private R<EnrollResult> rotateOrReturn(Long userId, SysUserMfa existing) {
        String secret;
        if (existing.getEnabled() == null || existing.getEnabled() == 0) {
            secret = TotpGenerator.generateSecret();
            existing.setSecret(secret);
            existing.setEnabled(0);
            existing.setEnrolledAt(null);
            existing.setBackupCodes(null);
            existing.setUpdateBy(userId);
            mfaMapper.updateById(existing);
        } else {
            secret = existing.getSecret();
        }
        SysUser user = userMapper.findByUserId(userId);
        String userName = (user != null && user.getUserName() != null)
            ? user.getUserName() : String.valueOf(userId);
        log.info("MFA enroll initiated: userId={}, active={}",
            userId, existing.getEnabled() != null && existing.getEnabled() == 1);
        return R.ok(new EnrollResult(secret, buildOtpauthUrl(userName, secret)));
    }

    /**
     * Confirm a freshly-staged secret with a valid TOTP code. Marks enrollment enabled,
     * generates 10 fresh backup codes, persists their bcrypt hashes, and returns the
     * plaintext codes (shown to the user once — never logged, never stored).
     * Throws 401 on bad code.
     */
    @Transactional
    public R<List<String>> confirm(String code) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new ServiceException(401, "Not authenticated");
        }
        SysUserMfa row = mfaMapper.selectById(userId);
        if (row == null) {
            throw new ServiceException(401, "No MFA enrollment in progress");
        }
        if (!TotpGenerator.verify(row.getSecret(), code, 1)) {
            // Generic message — do NOT leak whether secret exists.
            throw new ServiceException(401, "Invalid code");
        }

        List<String> codes = generateBackupCodes();
        // Purge any leftover backup codes from a prior enrollment (e.g. user re-confirmed
        // after a device reset). Old hashes become unreachable and the rows are deleted
        // in the same transaction as the new inserts.
        backupCodeMapper.deleteAllByUserId(userId);

        for (String plain : codes) {
            SysUserMfaBackupCode bc = new SysUserMfaBackupCode();
            bc.setUserId(userId);
            bc.setCodeHash(passwordEncoder.encode(plain));
            bc.setCreateBy(userId);
            bc.setUpdateBy(userId);
            backupCodeMapper.insert(bc);
        }

        row.setEnabled(1);
        row.setEnrolledAt(LocalDateTime.now());
        row.setBackupCodes(null); // legacy column — no longer written, kept NULL for compatibility
        row.setUpdateBy(userId);
        mfaMapper.updateById(row);

        // NB: codes are returned in the response body ONCE — never logged.
        log.info("MFA confirmed: userId={}, generated {} hashed backup codes", userId, codes.size());
        return R.ok(codes);
    }

    /**
     * Disable MFA for the calling user. Requires a valid current TOTP code so an
     * attacker who hijacks a session can't silently disable MFA.
     *
     * <p>TODO(I6): disabling MFA does NOT invalidate existing sessions. Out of scope
     * for P2; a compromised session can continue until its own expiry. Once we add
     * session revocation hooks here, also kick any active refresh tokens.</p>
     */
    @Transactional
    public R<Void> disable(String code) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new ServiceException(401, "Not authenticated");
        }
        SysUserMfa row = mfaMapper.selectById(userId);
        if (row == null || row.getEnabled() == null || row.getEnabled() == 0) {
            throw new ServiceException(401, "MFA not enrolled");
        }
        if (!TotpGenerator.verify(row.getSecret(), code, 1)) {
            throw new ServiceException(401, "Invalid code");
        }
        row.setEnabled(0);
        row.setSecret(null);
        row.setBackupCodes(null);
        row.setEnrolledAt(null);
        row.setUpdateBy(userId);
        mfaMapper.updateById(row);
        // Drop all unused backup codes so they can't be replayed after re-enrollment.
        backupCodeMapper.deleteAllUnusedByUserId(userId);
        log.info("MFA disabled: userId={}", userId);
        return R.ok();
    }

    /**
     * Verify a code against a user's secret. Returns false on any failure
     * (no row / disabled / bad code). Used by the step-up flow — must not throw.
     *
     * <p>Order of checks: TOTP first, then backup codes. TOTP win keeps bcrypt cost off
     * the hot path for normal logins. Backup code consumption is atomic via the
     * {@code UPDATE ... WHERE id=? AND used_at IS NULL} guard on the mapper — racing
     * double-redemptions see 0 rows updated and return false.</p>
     *
     * <p>TODO(I4): the calling flow already validated the user exists at login time,
     * but a stale token could outlive a deactivation. Re-check {@code sys_user.status}
     * here when wiring P3 hardening.</p>
     */
    public boolean verify(Long userId, String code) {
        if (userId == null || code == null) {
            return false;
        }
        SysUserMfa row = mfaMapper.selectById(userId);
        if (row == null || row.getEnabled() == null || row.getEnabled() == 0) {
            return false;
        }
        if (TotpGenerator.verify(row.getSecret(), code, 1)) {
            return true;
        }
        // TOTP failed — try backup codes (length-checked to keep the bcrypt loop bounded).
        if (code.length() != BACKUP_CODE_LENGTH) {
            return false;
        }
        return consumeBackupCode(userId, code, null);
    }

    /**
     * Atomic single-use redemption: select the unused rows for the user, find the one
     * whose hash matches, then flip {@code used_at} only if it was still NULL. The
     * WHERE clause on {@code markUsed} is the linearisation point — two parallel
     * redemptions of the same code race on the UPDATE, only one wins.
     */
    private boolean consumeBackupCode(Long userId, String plainCode, String usedIp) {
        List<SysUserMfaBackupCode> candidates = backupCodeMapper.selectUnusedByUserId(userId);
        for (SysUserMfaBackupCode candidate : candidates) {
            if (passwordEncoder.matches(plainCode, candidate.getCodeHash())) {
                int updated = backupCodeMapper.markUsed(candidate.getId(), userId, usedIp);
                if (updated == 1) {
                    log.info("MFA backup code consumed: userId={}, codeId={}", userId, candidate.getId());
                    return true;
                }
                // Lost the race — another request already consumed this code. Fall through.
                log.info("MFA backup code redemption lost race: userId={}, codeId={}", userId, candidate.getId());
            }
        }
        return false;
    }

    /** Is MFA actively enrolled for the given user? */
    public boolean isEnabled(Long userId) {
        if (userId == null) return false;
        SysUserMfa row = mfaMapper.selectById(userId);
        return row != null && row.getEnabled() != null && row.getEnabled() == 1;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static String buildOtpauthUrl(String userName, String secret) {
        // otpauth://totp/{label}?secret={secret}&issuer={issuer}
        // label = URL-encoded "Issuer:userName"
        String label = URLEncoder.encode(ISSUER + ":" + userName, StandardCharsets.UTF_8);
        String issuerEnc = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
            + "?secret=" + secret
            + "&issuer=" + issuerEnc;
    }

    private static List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                sb.append(BACKUP_ALPHABET[RNG.nextInt(BACKUP_ALPHABET.length)]);
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    /**
     * Enrollment result — secret + otpauth URI suitable for QR rendering.
     */
    public record EnrollResult(String secret, String otpauthUrl) {
    }
}