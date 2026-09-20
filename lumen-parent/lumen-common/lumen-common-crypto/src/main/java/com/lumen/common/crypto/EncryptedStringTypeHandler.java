package com.lumen.common.crypto;

import lombok.NoArgsConstructor;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis type handler that transparently encrypts {@link String} values on write and
 * decrypts them on read, using {@link AesGcmCipher} (AES-256-GCM).
 *
 * <p>Apply per-field via {@link com.baomidou.mybatisplus.annotation.TableField}:
 * <pre>
 *   {@code @TableField(typeHandler = EncryptedStringTypeHandler.class)}
 *   private String idCardEnc;
 * </pre>
 * This handler is intentionally NOT registered globally — encryption is opt-in per
 * field, so global configuration would silently encrypt fields the operator did not
 * intend to protect.</p>
 */
@NoArgsConstructor
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    /**
     * MyBatis reflectively instantiates {@code BaseTypeHandler} subclasses via
     * {@code Class.getConstructor()}, so a no-arg ctor is mandatory. We inject the
     * shared {@link AesGcmCipher} via {@link #setCipher(AesGcmCipher)} once from
     * {@code CryptoAutoConfiguration} after the Spring context is built; instances
     * created by MyBatis after that point observe a non-null cipher.
     */
    private static volatile AesGcmCipher CIPHER;

    /** Initialize the static cipher. Idempotent and thread-safe. */
    public static void setCipher(AesGcmCipher cipher) {
        CIPHER = cipher;
    }

    private AesGcmCipher cipher() {
        AesGcmCipher c = CIPHER;
        if (c == null) {
            throw new IllegalStateException(
                    "EncryptedStringTypeHandler used before CryptoAutoConfiguration initialized the cipher");
        }
        return c;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, cipher().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return cipher().decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return cipher().decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cipher().decrypt(cs.getString(columnIndex));
    }
}