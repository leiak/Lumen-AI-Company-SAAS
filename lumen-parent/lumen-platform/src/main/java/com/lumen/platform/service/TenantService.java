package com.lumen.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.platform.entity.Tenant;
import com.lumen.platform.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    public IPage<Tenant> list(int pageNum, int pageSize, String keyword) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
            .eq(Tenant::getDeleted, 0)
            .orderByDesc(Tenant::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Tenant::getCode, keyword).or().like(Tenant::getName, keyword));
        }
        return tenantMapper.selectPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(pageNum, pageSize),
            wrapper);
    }

    public Tenant getById(Long id) {
        Tenant t = tenantMapper.selectById(id);
        if (t == null) throw new ServiceException(404, "Tenant not found: " + id);
        return t;
    }

    @Transactional
    public Tenant create(Tenant tenant) {
        if (tenant.getCode() == null || tenant.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (tenantMapper.findByCode(tenant.getCode()) != null) {
            throw new ServiceException(409, "Tenant code already exists: " + tenant.getCode());
        }
        // Strip client-controlled fields to prevent spoofing
        Tenant toCreate = new Tenant();
        toCreate.setCode(tenant.getCode());
        toCreate.setName(tenant.getName());
        toCreate.setShortName(tenant.getShortName());
        toCreate.setContactName(tenant.getContactName());
        toCreate.setContactPhone(tenant.getContactPhone());
        toCreate.setContactEmail(tenant.getContactEmail());
        toCreate.setIndustry(tenant.getIndustry());
        toCreate.setScale(tenant.getScale());
        toCreate.setRegion(tenant.getRegion());
        toCreate.setPackageId(tenant.getPackageId());
        toCreate.setLogoUrl(tenant.getLogoUrl());
        toCreate.setDescription(tenant.getDescription());
        // Server-controlled fields
        toCreate.setStatus(tenant.getStatus() != null ? tenant.getStatus() : 1);
        toCreate.setTrialDays(tenant.getTrialDays() != null ? tenant.getTrialDays() : 30);
        toCreate.setExpireAt(tenant.getExpireAt() != null
            ? tenant.getExpireAt()
            : LocalDateTime.now().plusDays(toCreate.getTrialDays()));
        tenantMapper.insert(toCreate);
        log.info("Created tenant id={} code={}", toCreate.getId(), toCreate.getCode());
        return toCreate;
    }

    @Transactional
    public Tenant switchMode(Long id, String mode) {
        Tenant t = getById(id);
        // 1=private, 2=saas
        int newStatus = switch (mode) {
            case "private" -> 1;
            case "saas" -> 2;
            default -> throw new ServiceException(400, "Unknown mode: " + mode);
        };
        t.setStatus(newStatus);
        tenantMapper.updateById(t);
        log.info("Tenant {} mode -> {}", id, mode);
        return t;
    }
}
