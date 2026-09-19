package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Contact;
import com.lumen.sales.mapper.ContactMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 联系人服务。setPrimary 会取消旧的 primary。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private final ContactMapper contactMapper;
    private final CustomerService customerService;

    public IPage<Contact> page(int pageNum, int pageSize, Long customerId, String name) {
        UserContext ctx = requireContext();
        var w = new LambdaQueryWrapper<Contact>().orderByDesc(Contact::getId);
        if (customerId != null) w.eq(Contact::getCustomerId, customerId);
        if (name != null && !name.isBlank()) w.like(Contact::getName, name);
        return contactMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public List<Contact> findByCustomer(Long customerId) {
        UserContext ctx = requireContext();
        // Validate customer exists / tenant boundary
        customerService.get(customerId);
        return contactMapper.findByCustomer(customerId, ctx.getTenantId());
    }

    public Contact findPrimary(Long customerId) {
        UserContext ctx = requireContext();
        customerService.get(customerId);
        return contactMapper.findPrimary(customerId, ctx.getTenantId());
    }

    @Transactional
    public Contact save(Contact req) {
        UserContext ctx = requireContext();
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        if (req.getCustomerId() == null) {
            throw new ServiceException(400, "customerId is required");
        }
        customerService.get(req.getCustomerId());

        // 若设置为 primary,先把旧的取消
        if (Boolean.TRUE.equals(req.getIsPrimary())) {
            clearPrimaryForCustomer(req.getCustomerId(), ctx.getTenantId());
        }

        Contact toCreate = new Contact();
        toCreate.setCustomerId(req.getCustomerId());
        toCreate.setName(req.getName());
        toCreate.setPosition(req.getPosition());
        toCreate.setMobileEnc(req.getMobileEnc());
        toCreate.setEmailEnc(req.getEmailEnc());
        toCreate.setPhone(req.getPhone());
        toCreate.setIsPrimary(Boolean.TRUE.equals(req.getIsPrimary()));
        toCreate.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        toCreate.setTenantId(ctx.getTenantId());
        contactMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public Contact update(Contact req) {
        UserContext ctx = requireContext();
        if (req.getId() == null) throw new ServiceException(400, "id is required");
        Contact existing = get(req.getId());

        // 若设置为 primary,先把旧的取消
        if (Boolean.TRUE.equals(req.getIsPrimary())
            && (existing.getIsPrimary() == null || !existing.getIsPrimary())) {
            clearPrimaryForCustomer(existing.getCustomerId(), ctx.getTenantId());
        }

        if (req.getName() != null) existing.setName(req.getName());
        if (req.getPosition() != null) existing.setPosition(req.getPosition());
        if (req.getMobileEnc() != null) existing.setMobileEnc(req.getMobileEnc());
        if (req.getEmailEnc() != null) existing.setEmailEnc(req.getEmailEnc());
        if (req.getPhone() != null) existing.setPhone(req.getPhone());
        if (req.getIsPrimary() != null) existing.setIsPrimary(req.getIsPrimary());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        contactMapper.updateById(existing);
        return existing;
    }

    /** 设置某 contact 为主要联系人 — 取消同客户其他 contact 的 primary 标记。 */
    @Transactional
    public Contact setPrimary(Long contactId) {
        UserContext ctx = requireContext();
        Contact c = get(contactId);
        if (Boolean.TRUE.equals(c.getIsPrimary())) return c;
        clearPrimaryForCustomer(c.getCustomerId(), ctx.getTenantId());
        c.setIsPrimary(true);
        contactMapper.updateById(c);
        return c;
    }

    public Contact get(Long id) {
        Contact c = contactMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Contact not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(c.getTenantId())) {
                throw new ServiceException(404, "Contact not found: " + id);
            }
        }
        return c;
    }

    private void clearPrimaryForCustomer(Long customerId, Long tenantId) {
        List<Contact> existing = contactMapper.findByCustomer(customerId, tenantId);
        for (Contact c : existing) {
            if (Boolean.TRUE.equals(c.getIsPrimary())) {
                c.setIsPrimary(false);
                contactMapper.updateById(c);
            }
        }
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }
}