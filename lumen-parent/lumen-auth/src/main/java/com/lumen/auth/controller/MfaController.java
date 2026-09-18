package com.lumen.auth.controller;

import com.lumen.auth.service.MfaService;
import com.lumen.auth.service.MfaService.EnrollResult;
import com.lumen.common.core.domain.R;
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
 * MFA enrollment/disable endpoints (authenticated). The step-up {@code /auth/mfa/verify}
 * lives on {@link AuthController} because it returns a full {@code LoginResult} and
 * participates in the normal login flow.
 */
@Slf4j
@RestController
@RequestMapping("/mfa")
@RequiredArgsConstructor
@Validated
public class MfaController {

    private final MfaService mfaService;

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

    /** Minimal request body: just the TOTP code. */
    @Data
    public static class CodeRequest {
        @NotBlank
        private String code;
    }
}