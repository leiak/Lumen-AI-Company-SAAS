package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.SaveSupplierRequest;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.entity.ProcSupplierQualification;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import com.lumen.procurement.mapper.ProcSupplierQualificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商 CRUD + 黑名单 + 评级自动计算。
 *
 * <p>安全要点:</p>
 * <ul>
 *   <li>Tenant 隔离: 所有写操作第一行 {@link #requireCtx()} 校验。</li>
 *   <li>跨租户访问 → 404 (避免存在性泄漏)。</li>
 *   <li>rating 范围 [0,5]。</li>
 *   <li>黑名单后不能再被 inquiry 选入 (在 {@link ProcInquiryService} 校验)。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcSupplierService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_BLACKLIST = "blacklist";
    public static final String STATUS_PENDING = "pending";

    public static final int LEVEL_MIN = 1;
    public static final int LEVEL_MAX = 5;

    private final ProcSupplierMapper supplierMapper;
    private final ProcSupplierQualificationMapper qualificationMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<ProcSupplier> page(int pageNum, int pageSize, String keyword, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<ProcSupplier>().orderByDesc(ProcSupplier::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(ProcSupplier::getCode, keyword).or().like(ProcSupplier::getName, keyword));
        }
        if (status != null && !status.isBlank()) w.eq(ProcSupplier::getStatus, status);
        return supplierMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ProcSupplier getById(Long id) {
        UserContext ctx = requireCtx();
        ProcSupplier s = supplierMapper.selectById(id);
        if (s == null) throw new ServiceException(404, "Supplier not found: " + id);
        if (!s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Supplier not found: " + id);
        }
        return s;
    }

    @Transactional
    public ProcSupplier create(SaveSupplierRequest req) {
        UserContext ctx = requireCtx();
        validateRequest(req);
        if (supplierMapper.findByCode(ctx.getTenantId(), req.getCode()) != null) {
            throw new ServiceException(409, "Supplier code already exists: " + req.getCode());
        }
        ProcSupplier s = new ProcSupplier();
        s.setTenantId(ctx.getTenantId());
        s.setCode(req.getCode());
        s.setName(req.getName());
        s.setTaxNo(req.getTaxNo());
        s.setLevel(req.getLevel() == null ? LEVEL_MIN : req.getLevel());
        s.setStatus(req.getStatus() == null ? STATUS_PENDING : req.getStatus());
        s.setContactName(req.getContactName());
        s.setContactPhoneEnc(req.getContactPhoneEnc());
        s.setContactEmailEnc(req.getContactEmailEnc());
        s.setAddress(req.getAddress());
        s.setRating(req.getRating() == null ? BigDecimal.ZERO : req.getRating());
        try {
            supplierMapper.insert(s);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Supplier code conflict", ex);
        }
        log.info("Supplier created id={} code={}", s.getId(), s.getCode());
        return s;
    }

    @Transactional
    public ProcSupplier update(Long id, SaveSupplierRequest req) {
        ProcSupplier existing = getById(id);
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getTaxNo() != null) existing.setTaxNo(req.getTaxNo());
        if (req.getLevel() != null) {
            if (req.getLevel() < LEVEL_MIN || req.getLevel() > LEVEL_MAX) {
                throw new ServiceException(400, "level must be in [1,5]");
            }
            existing.setLevel(req.getLevel());
        }
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getContactName() != null) existing.setContactName(req.getContactName());
        if (req.getContactPhoneEnc() != null) existing.setContactPhoneEnc(req.getContactPhoneEnc());
        if (req.getContactEmailEnc() != null) existing.setContactEmailEnc(req.getContactEmailEnc());
        if (req.getAddress() != null) existing.setAddress(req.getAddress());
        if (req.getRating() != null) {
            if (req.getRating().compareTo(BigDecimal.ZERO) < 0
                || req.getRating().compareTo(new BigDecimal("5.00")) > 0) {
                throw new ServiceException(400, "rating must be in [0,5]");
            }
            existing.setRating(req.getRating());
        }
        supplierMapper.updateById(existing);
        return existing;
    }

    /**
     * 加入黑名单。已 blacklist → 409。
     */
    @Transactional
    public ProcSupplier blacklist(Long id, String reason) {
        ProcSupplier s = getById(id);
        if (STATUS_BLACKLIST.equals(s.getStatus())) {
            throw new ServiceException(409, "Supplier already blacklisted");
        }
        s.setStatus(STATUS_BLACKLIST);
        supplierMapper.updateById(s);
        log.info("Supplier blacklisted id={} reason={}", id, reason);
        return s;
    }

    /**
     * 解除黑名单。
     */
    @Transactional
    public ProcSupplier unblacklist(Long id) {
        ProcSupplier s = getById(id);
        s.setStatus(STATUS_ACTIVE);
        supplierMapper.updateById(s);
        return s;
    }

    /**
     * 列出该供应商的资质。
     */
    public List<ProcSupplierQualification> listQualifications(Long supplierId) {
        UserContext ctx = requireCtx();
        // 同样校验供应商存在 + 同租户
        getById(supplierId);
        return qualificationMapper.findBySupplier(ctx.getTenantId(), supplierId);
    }

    private void validateRequest(SaveSupplierRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        if (req.getTaxNo() == null || req.getTaxNo().isBlank()) {
            throw new ServiceException(400, "taxNo is required");
        }
        if (req.getLevel() != null && (req.getLevel() < LEVEL_MIN || req.getLevel() > LEVEL_MAX)) {
            throw new ServiceException(400, "level must be in [1,5]");
        }
        if (req.getRating() != null
            && (req.getRating().compareTo(BigDecimal.ZERO) < 0
                || req.getRating().compareTo(new BigDecimal("5.00")) > 0)) {
            throw new ServiceException(400, "rating must be in [0,5]");
        }
    }
}