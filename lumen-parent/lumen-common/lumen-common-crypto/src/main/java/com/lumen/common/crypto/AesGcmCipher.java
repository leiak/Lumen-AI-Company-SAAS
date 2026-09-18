package com.lumen.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM field cipher.
 *
 * <p>Envelope format (Base64 of): {@code [12-byte IV][ciphertext + 16-byte GCM tag]}.
 * The 128-bit GCM tag is automatically verified on decryption — any tampering with the
 * IV, ciphertext, or tag raises {@link javax.crypto.AEADBadTagException}.</p>
 *
 * <p>Keying: a 32-byte (256-bit) secret key. Construction fails fast with
 * {@link IllegalStateException} if the key is null or the wrong length. Configured by
 * {@link CryptoAutoConfiguration}, which reads the Base64-encoded key from
 * {@code lumen.security.crypto.key}.</p>
 */
public class AesGcmCipher {

    public static final int IV_LENGTH = 12;
    public static final int TAG_LENGTH_BITS = 128;
    public static final int KEY_LENGTH_BITS = 256;
    public static final int KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8;

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Construct with a raw 32-byte AES-256 key. The input array is zeroed after use
     * so the secret does not linger on the heap.
     */
    public AesGcmCipher(byte[] keyBytes) {
        if (keyBytes == null) {
            throw new IllegalStateException("AES key bytes are null");
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                "AES key must be exactly " + KEY_LENGTH_BYTES
                    + " bytes for AES-256, got " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        Arrays.fill(keyBytes, (byte) 0);
    }

    /**
     * Encrypt the given plaintext using a freshly generated 12-byte IV.
     *
     * @return Base64(IV || ciphertext+tag) suitable for storage in a single column
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[IV_LENGTH + ct.length];
            System.arraycopy(iv, 0, envelope, 0, IV_LENGTH);
            System.arraycopy(ct, 0, envelope, IV_LENGTH, ct.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) {
            // Never log the plaintext.
            throw new IllegalStateException("Encryption failure", e);
        }
    }

    /**
     * Decrypt an envelope produced by {@link #encrypt(String)}.
     *
     * @throws IllegalStateException on Base64 parse error, malformed envelope, or tag mismatch
     */
    public String decrypt(String envelope) {
        if (envelope == null) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(envelope);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Ciphertext is not valid Base64", e);
        }
        if (decoded.length < IV_LENGTH + TAG_LENGTH_BITS / 8) {
            throw new IllegalStateException("Ciphertext too short");
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
        int ctLen = decoded.length - IV_LENGTH;
        byte[] ciphertext = new byte[ctLen];
        System.arraycopy(decoded, IV_LENGTH, ciphertext, 0, ctLen);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] pt = cipher.doFinal(ciphertext);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failure (tampered ciphertext or wrong key)", e);
        }
    }

    // Visible for testing — static helper so unit tests can pass a key without going through config.
    static String decryptWithKey(byte[] keyBytes, String envelope) {
        return new AesGcmCipher(keyBytes.clone()).decrypt(envelope);
    }
}