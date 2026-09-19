package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.entity.ProcSupplierQualification;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import com.lumen.procurement.mapper.ProcSupplierQualificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 供应商资质。
 *
 * <p>状态机: pending → approved / rejected → expired (自动)。</p>
 * <p>approve/reject 仅在 pending 状态可执行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcSupplierQualificationService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_EXPIRED = "expired";

    private final ProcSupplierQualificationMapper qualificationMapper;
    private final ProcSupplierMapper supplierMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<ProcSupplierQualification> page(int pageNum, int pageSize, Long supplierId, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<ProcSupplierQualification>().orderByDesc(ProcSupplierQualification::getId);
        if (supplierId != null) w.eq(ProcSupplierQualification::getSupplierId, supplierId);
        if (status != null && !status.isBlank()) w.eq(ProcSupplierQualification::getStatus, status);
        return qualificationMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ProcSupplierQualification getById(Long id) {
        UserContext ctx = requireCtx();
        ProcSupplierQualification q = qualificationMapper.selectById(id);
        if (q == null) throw new ServiceException(404, "Qualification not found: " + id);
        if (!q.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Qualification not found: " + id);
        }
        // 自动 expired 标记 (lazy): expire_at 已过 且 status=approved → 翻转为 expired
        if (STATUS_APPROVED.equals(q.getStatus())
            && q.getExpireAt() != null
            && q.getExpireAt().isBefore(LocalDate.now())) {
            q.setStatus(STATUS_EXPIRED);
            qualificationMapper.updateById(q);
        }
        return q;
    }

    @Transactional
    public ProcSupplierQualification create(ProcSupplierQualification req) {
        UserContext ctx = requireCtx();
        if (req.getSupplierId() == null) throw new ServiceException(400, "supplierId is required");
        if (req.getQualificationType() == null) throw new ServiceException(400, "qualificationType is required");
        // supplier 同租户校验
        var supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null || !supplier.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(400, "Invalid supplierId");
        }
        ProcSupplierQualification q = new ProcSupplierQualification();
        q.setTenantId(ctx.getTenantId());
        q.setSupplierId(req.getSupplierId());
        q.setQualificationType(req.getQualificationType());
        q.setFileId(req.getFileId());
        q.setExpireAt(req.getExpireAt());
        q.setStatus(STATUS_PENDING);
        qualificationMapper.insert(q);
        log.info("Qualification created id={} supplier={} type={}",
            q.getId(), q.getSupplierId(), q.getQualificationType());
        return q;
    }

    /**
     * 资质审核通过。仅 pending 可批。
     */
    @Transactional
    public ProcSupplierQualification approve(Long id) {
        ProcSupplierQualification q = getById(id);
        if (!STATUS_PENDING.equals(q.getStatus())) {
            throw new ServiceException(409, "Only pending qualification can be approved (current=" + q.getStatus() + ")");
        }
        q.setStatus(STATUS_APPROVED);
        qualificationMapper.updateById(q);
        log.info("Qualification approved id={}", id);
        return q;
    }

    /**
     * 资质审核驳回。仅 pending 可驳。
     */
    @Transactional
    public ProcSupplierQualification reject(Long id) {
        ProcSupplierQualification q = getById(id);
        if (!STATUS_PENDING.equals(q.getStatus())) {
            throw new ServiceException(409, "Only pending qualification can be rejected (current=" + q.getStatus() + ")");
        }
        q.setStatus(STATUS_REJECTED);
        qualificationMapper.updateById(q);
        log.info("Qualification rejected id={}", id);
        return q;
    }

    /**
     * 查询 {@code now + days} 内将到期的资质 (默认 30/15/7)。
     */
    public List<ProcSupplierQualification> findExpiring(int days) {
        UserContext ctx = requireCtx();
        if (days < 1 || days > 365) {
            throw new ServiceException(400, "days must be in [1,365]");
        }
        return qualificationMapper.findExpiring(ctx.getTenantId(), days);
    }
}