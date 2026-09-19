package com.lumen.assets.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.assets.entity.AstCertificate;
import com.lumen.assets.mapper.AstCertificateMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 证照（营业执照/税务登记/ISO资质/其他）。
 * {@link #expiringWithin(Integer)} 默认返回 30/15/7 三档 status 标签。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    public static final String TYPE_BUSINESS_LICENSE = "business_license";
    public static final String TYPE_TAX_REGISTRATION = "tax_registration";
    public static final String TYPE_ISO_QUALIFICATION = "iso_qualification";
    public static final String TYPE_OTHER = "other";

    private final AstCertificateMapper certificateMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public AstCertificate getById(Long id) {
        UserContext ctx = requireCtx();
        AstCertificate c = certificateMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Certificate not found: " + id);
        if (!c.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Certificate not found: " + id);
        }
        return c;
    }

    public IPage<AstCertificate> page(int pageNum, int pageSize, String keyword, String type) {
        requireCtx();
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AstCertificate>()
            .orderByDesc(AstCertificate::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(AstCertificate::getCertificateNo, keyword)
                .or().like(AstCertificate::getHolder, keyword));
        }
        if (type != null && !type.isBlank()) w.eq(AstCertificate::getCertificateType, type);
        return certificateMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    @Transactional
    public AstCertificate create(AstCertificate req) {
        UserContext ctx = requireCtx();
        AstCertificate toCreate = new AstCertificate();
        toCreate.setTenantId(ctx.getTenantId());
        toCreate.setCertificateType(req.getCertificateType());
        toCreate.setCertificateNo(req.getCertificateNo());
        toCreate.setHolder(req.getHolder());
        toCreate.setIssueDate(req.getIssueDate());
        toCreate.setExpireAt(req.getExpireAt());
        toCreate.setFileUrl(req.getFileUrl());
        toCreate.setStatus(deriveStatus(req.getExpireAt()));
        certificateMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public AstCertificate update(Long id, AstCertificate req) {
        AstCertificate existing = getById(id);
        if (req.getCertificateType() != null) existing.setCertificateType(req.getCertificateType());
        if (req.getCertificateNo() != null) existing.setCertificateNo(req.getCertificateNo());
        if (req.getHolder() != null) existing.setHolder(req.getHolder());
        if (req.getIssueDate() != null) existing.setIssueDate(req.getIssueDate());
        if (req.getExpireAt() != null) {
            existing.setExpireAt(req.getExpireAt());
            existing.setStatus(deriveStatus(req.getExpireAt()));
        }
        if (req.getFileUrl() != null) existing.setFileUrl(req.getFileUrl());
        certificateMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        certificateMapper.deleteById(id);
    }

    /**
     * 查询即将到期的证照。返回的每条记录 status 字段为 {@code expired}/{@code critical (≤7)}
     * / {@code warning (≤15)}/{@code normal (≤30)}。
     *
     * @param days 默认 30，可指定任意正整数（结果限制在该窗口内）。
     */
    public List<AstCertificate> expiringWithin(Integer days) {
        requireCtx();
        int window = days == null || days <= 0 ? 30 : days;
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(window);
        List<AstCertificate> rows = certificateMapper.findExpiringWithin(endDate);
        List<AstCertificate> out = new ArrayList<>();
        for (AstCertificate c : rows) {
            c.setStatus(deriveStatus(c.getExpireAt()));
            out.add(c);
        }
        return out;
    }

    /**
     * 计算证照 status：已过期 → expired；剩余 ≤ 7 → critical；≤ 15 → warning；≤ 30 → normal；其它 → active。
     */
    public static String deriveStatus(LocalDate expireAt) {
        if (expireAt == null) return "active";
        LocalDate today = LocalDate.now();
        long daysToExpire = ChronoUnit.DAYS.between(today, expireAt);
        if (daysToExpire < 0) return "expired";
        if (daysToExpire <= 7) return "critical";
        if (daysToExpire <= 15) return "warning";
        if (daysToExpire <= 30) return "normal";
        return "active";
    }
}