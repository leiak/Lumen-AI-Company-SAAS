package com.lumen.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Base64;

/**
 * Auto-configuration for field-level AES-256-GCM encryption.
 *
 * <p>Wires {@link AesGcmCipher} with the 32-byte Base64 secret from
 * {@code lumen.security.crypto.key}, and exposes
 * {@link EncryptedStringTypeHandler} as a Spring bean for injection into
 * entity classes via {@link com.baomidou.mybatisplus.annotation.TableField}.</p>
 */
@Slf4j
@AutoConfiguration
public class CryptoAutoConfiguration {

    @Bean
    public AesGcmCipher aesGcmCipher(@Value("${lumen.security.crypto.key}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException(
                "lumen.security.crypto.key is required for field encryption. "
                    + "Set LUMEN_CRYPTO_KEY env var (Base64-encoded 32 bytes) "
                    + "or lumen.security.crypto.key in application.yml.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "lumen.security.crypto.key is not valid Base64", e);
        }
        if (keyBytes.length != AesGcmCipher.KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                "lumen.security.crypto.key must decode to "
                    + AesGcmCipher.KEY_LENGTH_BYTES + " bytes, got " + keyBytes.length);
        }
        AesGcmCipher cipher = new AesGcmCipher(keyBytes);
        log.info("AesGcmCipher initialized ({} bytes key)", keyBytes.length);
        return cipher;
    }

    // NOTE: not exposed as a Spring @Bean — registering EncryptedStringTypeHandler as a
    // bean causes MyBatis-Plus auto-detection to register it as the *default* TypeHandler
    // for every String column (replacing the built-in StringTypeHandler). That silently
    // encrypts every string parameter going through MyBatis, which breaks user-name
    // lookups and any other plain-text query. Entities that need field-level encryption
    // must reference this class explicitly via @TableField(typeHandler = ...); MyBatis-Plus
    // will instantiate it on demand. The AesGcmCipher bean above is the only required
    // Spring component for this module.
}