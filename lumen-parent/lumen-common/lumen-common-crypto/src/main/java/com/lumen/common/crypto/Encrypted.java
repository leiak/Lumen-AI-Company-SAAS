package com.lumen.common.crypto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation documenting that the annotated field holds a value that should be
 * persisted encrypted at rest. Apply alongside
 * {@link com.baomidou.mybatisplus.annotation.TableField}{@code (typeHandler =
 * EncryptedStringTypeHandler.class)} on the entity field; this marker exists purely
 * for documentation, code-search (e.g., "find all fields that are encrypted"), and to
 * make intent obvious to future maintainers.
 *
 * <p>{@link #alias()} is reserved for future use (e.g., alias-based decryption key
 * selection when the same field may be encrypted differently across tenants); v1
 * ignores it.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Encrypted {
    String[] alias() default {};
}