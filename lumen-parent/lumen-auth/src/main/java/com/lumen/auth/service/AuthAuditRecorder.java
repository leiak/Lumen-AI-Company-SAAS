package com.lumen.auth.service;

import com.lumen.auth.entity.SysAuthAudit;
import com.lumen.auth.mapper.SysAuthAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Separate bean so @Async works (intra-class calls bypass Spring's proxy).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthAuditRecorder {

    private final SysAuthAuditMapper authAuditMapper;

    @Async
    public void record(SysAuthAudit audit) {
        try {
            authAuditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("audit insert failed: {}", e.getMessage());
        }
    }
}
