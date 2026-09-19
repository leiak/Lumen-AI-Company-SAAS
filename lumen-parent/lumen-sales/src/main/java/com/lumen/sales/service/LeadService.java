package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.entity.Lead;
import com.lumen.sales.mapper.CustomerMapper;
import com.lumen.sales.mapper.LeadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 销售线索服务。
 * <p>
 * convertToCustomer:创建新 customer(进入公海)+ lead.status=converted + lead.converted_customer_id 关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    public static final String STATUS_NEW = "new";
    public static final String STATUS_CONTACTING = "contacting";
    public static final String STATUS_QUALIFIED = "qualified";
    public static final String STATUS_LOST = "lost";
    public static final String STATUS_CONVERTED = "converted";

    private final LeadMapper leadMapper;
    private final CustomerMapper customerMapper;
    private final CustomerService customerService;

    public IPage<Lead> page(int pageNum, int pageSize, String status, String source, Long ownerLevel) {
        UserContext ctx = requireContext();
        var w = new LambdaQueryWrapper<Lead>().orderByDesc(Lead::getId);
        if (status != null && !status.isBlank()) w.eq(Lead::getStatus, status);
        if (source != null && !source.isBlank()) w.eq(Lead::getSource, source);
        boolean isAdminLike = customerService.hasAnyRole(ctx, "super_admin", "admin", "sales_admin");
        Long ownerFilter = ownerLevel;
        if (ownerFilter == null && !isAdminLike) {
            ownerFilter = ctx.getUserId();
        }
        if (ownerFilter != null) w.eq(Lead::getOwnerUserId, ownerFilter);
        return leadMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Lead get(Long id) {
        Lead l = leadMapper.selectById(id);
        if (l == null) throw new ServiceException(404, "Lead not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(l.getTenantId())) {
                throw new ServiceException(404, "Lead not found: " + id);
            }
        }
        return l;
    }

    @Transactional
    public Lead save(Lead req) {
        UserContext ctx = requireContext();
        if (req.getCustomerName() == null || req.getCustomerName().isBlank()) {
            throw new ServiceException(400, "customerName is required");
        }
        Lead toCreate = new Lead();
        toCreate.setCustomerName(req.getCustomerName());
        toCreate.setContactName(req.getContactName());
        toCreate.setMobileEnc(req.getMobileEnc());
        toCreate.setSource(req.getSource());
        toCreate.setRequirement(req.getRequirement());
        toCreate.setEstimatedValue(req.getEstimatedValue());
        toCreate.setOwnerUserId(req.getOwnerUserId() == null ? ctx.getUserId() : req.getOwnerUserId());
        toCreate.setStatus(STATUS_NEW);
        toCreate.setTenantId(ctx.getTenantId());
        toCreate.setLastFollowupAt(LocalDateTime.now());
        leadMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public Lead update(Lead req) {
        if (req.getId() == null) throw new ServiceException(400, "id is required");
        Lead existing = get(req.getId());
        if (STATUS_CONVERTED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Converted lead cannot be edited");
        }
        if (req.getCustomerName() != null) existing.setCustomerName(req.getCustomerName());
        if (req.getContactName() != null) existing.setContactName(req.getContactName());
        if (req.getMobileEnc() != null) existing.setMobileEnc(req.getMobileEnc());
        if (req.getSource() != null) existing.setSource(req.getSource());
        if (req.getRequirement() != null) existing.setRequirement(req.getRequirement());
        if (req.getEstimatedValue() != null) existing.setEstimatedValue(req.getEstimatedValue());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setLastFollowupAt(LocalDateTime.now());
        leadMapper.updateById(existing);
        return existing;
    }

    /**
     * Lead → Customer 转换。创建一个新客户 (status=in_pool),lead 标记为 converted 并关联。
     */
    @Transactional
    public Customer convertToCustomer(Long leadId) {
        UserContext ctx = requireContext();
        Lead lead = get(leadId);
        if (STATUS_CONVERTED.equals(lead.getStatus())) {
            throw new ServiceException(409, "Lead already converted");
        }
        if (STATUS_LOST.equals(lead.getStatus())) {
            throw new ServiceException(409, "Lost lead cannot be converted");
        }

        Customer c = new Customer();
        c.setCode("L-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setName(lead.getCustomerName());
        c.setSource(lead.getSource());
        c.setIndustry(null);
        c.setScale(null);
        c.setStatus(CustomerService.STATUS_IN_POOL);
        c.setOwnerUserId(null);
        c.setTenantId(ctx.getTenantId());
        try {
            customerMapper.insert(c);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Customer code conflict", ex);
        }

        lead.setStatus(STATUS_CONVERTED);
        lead.setConvertedCustomerId(c.getId());
        leadMapper.updateById(lead);
        log.info("Lead {} converted to customer {}", leadId, c.getId());
        return c;
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }
}