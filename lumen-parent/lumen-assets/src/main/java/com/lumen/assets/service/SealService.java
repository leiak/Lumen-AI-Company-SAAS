package com.lumen.assets.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.assets.entity.AstSeal;
import com.lumen.assets.entity.AstSealUsage;
import com.lumen.assets.mapper.AstSealMapper;
import com.lumen.assets.mapper.AstSealUsageMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 印章 + 用印记录。状态机：in_use → sealed → destroyed。
 * 销毁前必须没有 active 的 usage 记录（即所有用印已归还）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SealService {

    public static final String SEAL_TYPE_COMPANY = "company";
    public static final String SEAL_TYPE_CONTRACT = "contract";
    public static final String SEAL_TYPE_FINANCE = "finance";
    public static final String SEAL_TYPE_LEGAL = "legal";

    public static final String STATUS_IN_USE = "in_use";
    public static final String STATUS_SEALED = "sealed";
    public static final String STATUS_DESTROYED = "destroyed";

    private final AstSealMapper sealMapper;
    private final AstSealUsageMapper sealUsageMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public AstSeal getById(Long id) {
        UserContext ctx = requireCtx();
        AstSeal s = sealMapper.selectById(id);
        if (s == null) throw new ServiceException(404, "Seal not found: " + id);
        if (!s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Seal not found: " + id);
        }
        return s;
    }

    public IPage<AstSeal> page(int pageNum, int pageSize, String keyword, String sealType, String status) {
        requireCtx();
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AstSeal>()
            .orderByDesc(AstSeal::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(AstSeal::getCode, keyword).or().like(AstSeal::getName, keyword));
        }
        if (sealType != null && !sealType.isBlank()) w.eq(AstSeal::getSealType, sealType);
        if (status != null && !status.isBlank()) w.eq(AstSeal::getStatus, status);
        return sealMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    @Transactional
    public AstSeal create(AstSeal req) {
        UserContext ctx = requireCtx();
        AstSeal toCreate = new AstSeal();
        toCreate.setTenantId(ctx.getTenantId());
        toCreate.setCode(req.getCode());
        toCreate.setName(req.getName());
        toCreate.setSealType(req.getSealType());
        toCreate.setKeeperId(req.getKeeperId());
        toCreate.setStatus(STATUS_IN_USE);
        sealMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public AstSeal update(Long id, AstSeal req) {
        AstSeal existing = getById(id);
        if (STATUS_DESTROYED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Destroyed seal is immutable");
        }
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getSealType() != null) existing.setSealType(req.getSealType());
        if (req.getKeeperId() != null) existing.setKeeperId(req.getKeeperId());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        sealMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        sealMapper.deleteById(id);
    }

    /**
     * 用印：写入一条 usage 记录（returned_at=null），并把 seal.status 切到 in_use。
     */
    @Transactional
    public AstSealUsage use(Long sealId, String documentName, String documentId,
                            Long userId, Long witnessId) {
        UserContext ctx = requireCtx();
        AstSeal seal = getById(sealId);
        if (STATUS_DESTROYED.equals(seal.getStatus())) {
            throw new ServiceException(409, "Cannot use destroyed seal");
        }
        if (STATUS_SEALED.equals(seal.getStatus())) {
            throw new ServiceException(409, "Seal is sealed; unseal first");
        }
        AstSealUsage usage = new AstSealUsage();
        usage.setTenantId(ctx.getTenantId());
        usage.setSealId(sealId);
        usage.setDocumentName(documentName);
        usage.setDocumentId(documentId);
        usage.setUserId(userId);
        usage.setUsedAt(LocalDateTime.now());
        usage.setWitnessId(witnessId);
        sealUsageMapper.insert(usage);
        seal.setStatus(STATUS_IN_USE);
        sealMapper.updateById(seal);
        log.info("Seal used id={} seal={} doc={}", usage.getId(), sealId, documentName);
        return usage;
    }

    /**
     * 归还用印：把 usage.returned_at 填上，usage 状态保留在表中供审计。
     */
    @Transactional
    public AstSealUsage returnUsage(Long usageId) {
        UserContext ctx = requireCtx();
        AstSealUsage usage = sealUsageMapper.selectById(usageId);
        if (usage == null) throw new ServiceException(404, "Seal usage not found: " + usageId);
        if (!usage.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Seal usage not found: " + usageId);
        }
        if (usage.getReturnedAt() != null) {
            throw new ServiceException(409, "Seal usage already returned");
        }
        usage.setReturnedAt(LocalDateTime.now());
        sealUsageMapper.updateById(usage);
        return usage;
    }

    /**
     * 销毁印章：必须没有未归还的 usage 记录。
     */
    @Transactional
    public AstSeal destroy(Long id, String reason) {
        AstSeal existing = getById(id);
        if (STATUS_DESTROYED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Seal already destroyed");
        }
        List<AstSealUsage> usages = sealUsageMapper.findBySeal(id);
        if (usages != null) {
            for (AstSealUsage u : usages) {
                if (u.getReturnedAt() == null) {
                    throw new ServiceException(409,
                        "Cannot destroy seal with active usage (usageId=" + u.getId() + ")");
                }
            }
        }
        existing.setStatus(STATUS_DESTROYED);
        sealMapper.updateById(existing);
        log.info("Seal destroyed id={} reason={}", id, reason);
        return existing;
    }

    public List<AstSealUsage> findUsages(Long sealId) {
        requireCtx();
        return sealUsageMapper.findBySeal(sealId);
    }
}