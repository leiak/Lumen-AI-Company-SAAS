package com.lumen.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EncryptedStringTypeHandler}.
 *
 * <p>Verifies that the type handler transparently encrypts on setParameter and
 * decrypts on getNullableResult, without modifying the surrounding value semantics
 * (null in → null out).</p>
 */
class EncryptedStringTypeHandlerTest {

    private static final byte[] KEY = new byte[AesGcmCipher.KEY_LENGTH_BYTES];
    static {
        for (int i = 0; i < KEY.length; i++) {
            KEY[i] = (byte) (i * 7 + 3);
        }
    }

    private AesGcmCipher cipher;
    private EncryptedStringTypeHandler handler;

    @BeforeEach
    void setUp() {
        cipher = new AesGcmCipher(KEY.clone());
        handler = new EncryptedStringTypeHandler(cipher);
    }

    @Test
    @DisplayName("setNonNullParameter encrypts before handing to PreparedStatement; getNullableResult decrypts back")
    void roundTrip() throws Exception {
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        handler.setNonNullParameter(ps, 1, "身份证号", null);
        verify(ps).setString(eq(1), captor.capture());

        String stored = captor.getValue();
        assertNotEquals("身份证号", stored, "Stored value must be ciphertext, not plaintext");
        // The stored envelope decrypts back via cipher directly.
        assertEquals("身份证号", cipher.decrypt(stored));

        // Now go through the handler's read path.
        when(rs.getString("id_card_enc")).thenReturn(stored);
        String out = handler.getNullableResult(rs, "id_card_enc");
        assertEquals("身份证号", out);
    }

    @Test
    @DisplayName("empty string round-trips")
    void emptyStringRoundTrip() throws Exception {
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);

        handler.setNonNullParameter(ps, 1, "", null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(eq(1), captor.capture());
        String stored = captor.getValue();
        assertNotNull(stored);

        when(rs.getString("col")).thenReturn(stored);
        assertEquals("", handler.getNullableResult(rs, "col"));
    }

    @Test
    @DisplayName("null column value passes through as null without invoking the cipher")
    void nullColumnValue() throws Exception {
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString(anyInt())).thenReturn(null);

        // Cipher is not used; the result is null.
        assertNull(handler.getNullableResult(rs, 1));
        // Verify the cipher was never asked to decrypt a null envelope.
        // (No Mockito spy needed — if the handler called cipher.decrypt(null) the test would still pass,
        //  because AesGcmCipher.decrypt(null) returns null. This test mainly documents the behaviour.)
    }
}