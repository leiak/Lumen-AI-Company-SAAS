package com.lumen.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AesGcmCipher}. Pure JDK — no Spring context required.
 *
 * <p>Test vectors use a fixed 32-byte AES-256 key derived from a deterministic pattern
 * so failures are reproducible.</p>
 */
class AesGcmCipherTest {

    private static final byte[] KEY = new byte[AesGcmCipher.KEY_LENGTH_BYTES];
    static {
        // Deterministic 32-byte key — fine for unit tests.
        for (int i = 0; i < KEY.length; i++) {
            KEY[i] = (byte) (i + 1);
        }
    }

    private AesGcmCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new AesGcmCipher(KEY.clone());
    }

    @Test
    @DisplayName("encrypt/decrypt round-trips")
    void roundTrip() {
        String plaintext = "hello, world";
        String envelope = cipher.encrypt(plaintext);
        assertNotNull(envelope);
        assertEquals(plaintext, cipher.decrypt(envelope));
    }

    @Test
    @DisplayName("two encryptions of the same plaintext yield different ciphertexts (random IV)")
    void differentEncryptionsUseDifferentIvs() {
        String plaintext = "duplicate";
        String a = cipher.encrypt(plaintext);
        String b = cipher.encrypt(plaintext);
        assertNotEquals(a, b, "GCM IV reuse would produce identical envelopes");
        // Both still decrypt to the same plaintext.
        assertEquals(plaintext, cipher.decrypt(a));
        assertEquals(plaintext, cipher.decrypt(b));
    }

    @Test
    @DisplayName("envelope is valid Base64 that decodes to >= IV+TAG bytes")
    void envelopeIsBase64() {
        String envelope = cipher.encrypt("anything");
        byte[] decoded = Base64.getDecoder().decode(envelope);
        assertTrue(decoded.length >= AesGcmCipher.IV_LENGTH + AesGcmCipher.TAG_LENGTH_BITS / 8);
    }

    @Test
    @DisplayName("tampering with ciphertext bytes causes GCM tag verification to fail")
    void tamperedCiphertextThrows() {
        String envelope = cipher.encrypt("important data");
        byte[] bytes = Base64.getDecoder().decode(envelope);
        // Flip a bit inside the ciphertext region (after IV).
        bytes[AesGcmCipher.IV_LENGTH + 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    @DisplayName("tampering with IV causes GCM tag verification to fail")
    void tamperedIvThrows() {
        String envelope = cipher.encrypt("important data");
        byte[] bytes = Base64.getDecoder().decode(envelope);
        // Flip a bit inside the IV region.
        bytes[0] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    @DisplayName("empty plaintext round-trips")
    void emptyPlaintextRoundTrip() {
        String envelope = cipher.encrypt("");
        assertNotNull(envelope);
        assertEquals("", cipher.decrypt(envelope));
    }

    @Test
    @DisplayName("Unicode (Chinese) round-trips")
    void unicodeRoundTrip() {
        String plaintext = "你好，世界 — 加密测试 123 ✓";
        String envelope = cipher.encrypt(plaintext);
        assertEquals(plaintext, cipher.decrypt(envelope));
    }

    @Test
    @DisplayName("1 MB plaintext round-trips")
    void largePlaintextRoundTrip() {
        byte[] big = new byte[1024 * 1024];
        Arrays.fill(big, (byte) 'x');
        String plaintext = new String(big, StandardCharsets.UTF_8);
        String envelope = cipher.encrypt(plaintext);
        String decoded = cipher.decrypt(envelope);
        assertEquals(plaintext.length(), decoded.length());
        assertEquals(plaintext, decoded);
    }

    @Test
    @DisplayName("constructor rejects a key of wrong length")
    void constructorRejectsShortKey() {
        byte[] tooShort = new byte[16];
        assertThrows(IllegalStateException.class, () -> new AesGcmCipher(tooShort));
        byte[] tooLong = new byte[64];
        assertThrows(IllegalStateException.class, () -> new AesGcmCipher(tooLong));
    }

    @Test
    @DisplayName("constructor rejects a null key")
    void constructorRejectsNullKey() {
        assertThrows(IllegalStateException.class, () -> new AesGcmCipher(null));
    }

    @Test
    @DisplayName("encryption with a different key fails to decrypt")
    void differentKeyCannotDecrypt() {
        String envelope = cipher.encrypt("secret");
        byte[] otherKey = new byte[AesGcmCipher.KEY_LENGTH_BYTES];
        Arrays.fill(otherKey, (byte) 0xFF);
        AesGcmCipher other = new AesGcmCipher(otherKey);
        assertThrows(IllegalStateException.class, () -> other.decrypt(envelope));
    }
}