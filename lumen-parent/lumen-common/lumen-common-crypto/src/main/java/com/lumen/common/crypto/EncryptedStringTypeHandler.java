package com.lumen.common.crypto;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    private final AesGcmCipher cipher;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, cipher.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return cipher.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return cipher.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cipher.decrypt(cs.getString(columnIndex));
    }
}