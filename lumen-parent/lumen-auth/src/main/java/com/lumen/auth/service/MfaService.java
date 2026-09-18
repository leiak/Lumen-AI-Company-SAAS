package com.lumen.auth.service;

import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserMfa;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.auth.mapper.SysUserMfaMapper;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.common.security.mfa.TotpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
 * (0/O, 1/I/L). Returned to the user once at {@link #confirm(String)}; thereafter
 * the user must re-enroll to regenerate them. (Re-issuing individual backup codes
 * is out of scope for P2.)</p>
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
    private final SysUserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate; // reserved for future rate-limit/cache use

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
        String secret;
        if (existing == null) {
            secret = TotpGenerator.generateSecret();
            SysUserMfa row = new SysUserMfa();
            row.setUserId(userId);
            row.setSecret(secret);
            row.setEnabled(0);
            row.setUpdateBy(userId);
            mfaMapper.insert(row);
        } else if (existing.getEnabled() == null || existing.getEnabled() == 0) {
            // Pending / disabled enrollment: rotate the secret so any leaked QR is
            // no longer valid, and clear stale state.
            secret = TotpGenerator.generateSecret();
            existing.setSecret(secret);
            existing.setEnabled(0);
            existing.setEnrolledAt(null);
            existing.setBackupCodes(null);
            existing.setUpdateBy(userId);
            mfaMapper.updateById(existing);
        } else {
            // Active enrollment: surface re-enrollment path by returning the existing
            // secret untouched. The caller can re-scan the QR on a new device.
            secret = existing.getSecret();
        }

        String userName = user.getUserName() == null ? String.valueOf(userId) : user.getUserName();
        String otpauthUrl = buildOtpauthUrl(userName, secret);
        log.info("MFA enroll initiated: userId={}, active={}",
            userId, existing != null && existing.getEnabled() != null && existing.getEnabled() == 1);
        return R.ok(new EnrollResult(secret, otpauthUrl));
    }

    /**
     * Confirm a freshly-staged secret with a valid TOTP code. Marks enrollment enabled
     * and returns one-time backup codes. Throws 401 on bad code.
     */
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
        row.setEnabled(1);
        row.setEnrolledAt(LocalDateTime.now());
        row.setUpdateBy(userId);
        List<String> codes = generateBackupCodes();
        row.setBackupCodes(String.join(",", codes));
        mfaMapper.updateById(row);
        // NB: codes are returned in the response body ONCE — never logged.
        log.info("MFA confirmed: userId={}", userId);
        return R.ok(codes);
    }

    /**
     * Disable MFA for the calling user. Requires a valid current TOTP code so an
     * attacker who hijacks a session can't silently disable MFA.
     */
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
        log.info("MFA disabled: userId={}", userId);
        return R.ok();
    }

    /**
     * Verify a code against a user's secret. Returns false on any failure
     * (no row / disabled / bad code). Used by the step-up flow — must not throw.
     */
    public boolean verify(Long userId, String code) {
        if (userId == null || code == null) {
            return false;
        }
        SysUserMfa row = mfaMapper.selectById(userId);
        if (row == null || row.getEnabled() == null || row.getEnabled() == 0) {
            return false;
        }
        return TotpGenerator.verify(row.getSecret(), code, 1);
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