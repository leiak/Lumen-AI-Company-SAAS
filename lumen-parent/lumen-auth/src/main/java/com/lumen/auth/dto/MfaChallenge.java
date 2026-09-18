package com.lumen.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Returned from {@code POST /auth/login} when the user must complete an MFA step-up
 * before a full JWT is issued. The caller presents {@code mfaToken} back to
 * {@code POST /mfa/verify} together with a 6-digit TOTP code.
 *
 * <p>{@code mfaToken} is a JWT with {@code type=mfa} and {@code mfa_token=1} claims
 * and a 5-minute TTL — it is NOT accepted by the gateway for resource access.</p>
 */
@Data
@AllArgsConstructor
public class MfaChallenge {
    /** Short-lived JWT (5 min) carrying user identity + mfa_token=1 claim. */
    private String mfaToken;
    /** TTL in seconds (300 by default). */
    private long expiresIn;
}