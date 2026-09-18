package com.lumen.common.security.mfa;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * RFC 6238 TOTP generator with hand-rolled RFC 4648 Base32 (no padding).
 *
 * <p>Pure JDK — uses {@link Mac#getInstance(String)} for HMAC-SHA1 and {@link SecureRandom}
 * for secret generation. No external TOTP/Base32 library is required.</p>
 *
 * <p>Time-step is fixed at 30s per RFC 6238 recommendation. Digits are fixed at 6.
 * Verification accepts codes within a configurable symmetric window of ±N steps to
 * tolerate client clock drift.</p>
 */
@Slf4j
public final class TotpGenerator {

    /** RFC 6238 recommends 30-second time step. */
    public static final int TIME_STEP_SECONDS = 30;
    /** 6-digit codes are the de-facto industry default (Google, Authy, Authenticator). */
    public static final int CODE_DIGITS = 6;
    /** 20 bytes (160 bits) is the secret size used by most authenticator apps. */
    public static final int SECRET_BYTES = 20;
    /** HMAC-SHA1 algorithm (RFC 6238 default). */
    private static final String HMAC_ALG = "HmacSHA1";
    /** Base32 alphabet (RFC 4648). */
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private static final SecureRandom RNG = new SecureRandom();

    private TotpGenerator() {
    }

    /**
     * Generate a fresh 20-byte Base32 (no-padding) secret.
     */
    public static String generateSecret() {
        byte[] raw = new byte[SECRET_BYTES];
        RNG.nextBytes(raw);
        return encodeBase32(raw);
    }

    /**
     * Compute the current 6-digit TOTP for {@code secret}.
     */
    public static String currentCode(String secret) {
        return currentCode(secret, Instant.now().getEpochSecond());
    }

    /**
     * Compute the TOTP for a given Unix epoch second. Package-private to allow deterministic
     * testing of RFC 6238 vectors.
     */
    static String currentCode(String secret, long epochSeconds) {
        long counter = epochSeconds / TIME_STEP_SECONDS;
        return generateCode(secret, counter);
    }

    /**
     * Verify {@code code} against {@code secret}, accepting codes within ±window steps.
     * Uses constant-time comparison.
     *
     * @param secret Base32 secret (no padding)
     * @code code  candidate 6-digit code (non-numeric input returns false)
     * @param window number of steps to look ahead/behind (e.g. 1 = accept t-1, t, t+1)
     */
    public static boolean verify(String secret, String code, int window) {
        if (secret == null || code == null || code.length() != CODE_DIGITS) {
            return false;
        }
        // Cheap pre-check: reject non-digits early. Note this leaks digit-vs-non-digit
        // via timing, but is intentional — the secret is what's load-bearing and we still
        // do constant-time compare per step below.
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        long counter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (int offset = -window; offset <= window; offset++) {
            String expected = generateCode(secret, counter + offset);
            if (constantTimeEquals(expected, code)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    private static String generateCode(String secret, long counter) {
        byte[] key = decodeBase32(secret);
        byte[] msg = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(key, HMAC_ALG));
            byte[] hmac = mac.doFinal(msg);
            // RFC 6238 dynamic truncation: last 4 bits of hmac are the offset.
            int offset = hmac[hmac.length - 1] & 0x0F;
            int binary = ((hmac[offset] & 0x7F) << 24)
                | ((hmac[offset + 1] & 0xFF) << 16)
                | ((hmac[offset + 2] & 0xFF) << 8)
                | (hmac[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            // Should never happen — HmacSHA1 is mandatory in every JCE provider — but surface
            // as a runtime exception with the secret value never included in the message.
            throw new IllegalStateException("HMAC-SHA1 unavailable in current JCE", e);
        }
    }

    // ---------------------------------------------------------------------
    // Base32 (RFC 4648, no padding)
    // ---------------------------------------------------------------------

    static String encodeBase32(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                int idx = (buffer >> bitsLeft) & 0x1F;
                sb.append(BASE32_ALPHABET[idx]);
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET[idx]);
        }
        return sb.toString();
    }

    /**
     * Decode RFC 4648 Base32 (no padding required). Tolerant of lowercase input and spaces;
     * throws {@link IllegalArgumentException} on invalid characters.
     */
    static byte[] decodeBase32(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Base32 input is null");
        }
        String s = input.replace(" ", "").replace("-", "").toUpperCase();
        if (s.isEmpty()) {
            return new byte[0];
        }
        int outLen = s.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0;
        int bitsLeft = 0;
        int idx = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int v = -1;
            if (c >= 'A' && c <= 'Z') {
                v = c - 'A';
            } else if (c >= '2' && c <= '7') {
                v = 26 + (c - '2');
            }
            if (v < 0) {
                throw new IllegalArgumentException("Invalid Base32 character at index " + i + ": '" + c + "'");
            }
            buffer = (buffer << 5) | v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[idx++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out;
    }

    /**
     * Constant-time string equality. Both strings must be the same length — for our usage
     * the candidate is always exactly {@link #CODE_DIGITS} chars.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}