package com.lumen.auth.controller;

import com.lumen.auth.dto.LoginResult;
import com.lumen.auth.service.LoginService;
import com.lumen.auth.service.MfaService;
import com.lumen.auth.service.MfaService.EnrollResult;
import com.lumen.common.core.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MFA endpoints. Authenticated enrollment / confirm / disable live at
 * {@code /mfa/enroll|confirm|disable}. The step-up endpoint
 * {@code POST /mfa/verify} completes the login flow and is callable without an
 * authenticated session (the mfa-token carries identity).
 */
@Slf4j
@RestController
@RequestMapping("/mfa")
@RequiredArgsConstructor
@Validated
public class MfaController {

    private final MfaService mfaService;
    private final LoginService loginService;

    /** Begin or resume enrollment — returns the secret + otpauth URI for QR rendering. */
    @PostMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    public R<EnrollResult> enroll() {
        return mfaService.enroll();
    }

    /** Confirm enrollment with a TOTP code — returns one-time backup codes. */
    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    public R<List<String>> confirm(@RequestBody @Validated CodeRequest req) {
        return mfaService.confirm(req.getCode());
    }

    /** Disable MFA for the calling user — requires a valid current TOTP code. */
    @PostMapping("/disable")
    @PreAuthorize("isAuthenticated()")
    public R<Void> disable(@RequestBody @Validated CodeRequest req) {
        return mfaService.disable(req.getCode());
    }

    /**
     * Complete MFA step-up. The body carries the short-lived mfaToken returned from
     * {@code /auth/login} plus a 6-digit TOTP code. On success returns a full
     * {@link LoginResult} with a session row written via {@code SessionService}.
     */
    @PostMapping("/verify")
    public R<LoginResult> verify(@RequestBody @Validated VerifyRequest req,
                                 HttpServletRequest http) {
        return R.ok(loginService.verifyMfa(req.getMfaToken(), req.getCode(), http));
    }

    /** Minimal request body: just the TOTP code (enroll/confirm/disable). */
    @Data
    public static class CodeRequest {
        @NotBlank
        private String code;
    }

    /** Step-up verify body: short-lived mfaToken + 6-digit TOTP code. */
    @Data
    public static class VerifyRequest {
        @NotBlank
        private String mfaToken;
        @NotBlank
        private String code;
    }
}