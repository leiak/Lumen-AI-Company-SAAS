package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.dto.QuotationItemDto;
import com.lumen.sales.entity.Order;
import com.lumen.sales.entity.Quotation;
import com.lumen.sales.entity.QuotationItem;
import com.lumen.sales.mapper.OrderMapper;
import com.lumen.sales.mapper.QuotationItemMapper;
import com.lumen.sales.mapper.QuotationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 报价单服务。多版本 — 新版本号 = 上一版本 + 1,自动复制 items。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_EXPIRED = "expired";

    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final OpportunityService opportunityService;
    private final OrderMapper orderMapper;
    private final CustomerService customerService;

    public IPage<Quotation> page(int pageNum, int pageSize, String status, Long opportunityId) {
        var w = new LambdaQueryWrapper<Quotation>().orderByDesc(Quotation::getId);
        if (status != null && !status.isBlank()) w.eq(Quotation::getStatus, status);
        if (opportunityId != null) w.eq(Quotation::getOpportunityId, opportunityId);
        return quotationMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Quotation get(Long id) {
        Quotation q = quotationMapper.selectById(id);
        if (q == null) throw new ServiceException(404, "Quotation not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(q.getTenantId())) {
                throw new ServiceException(404, "Quotation not found: " + id);
            }
        }
        return q;
    }

    public List<QuotationItem> findItems(Long quotationId) {
        UserContext ctx = requireContext();
        return quotationItemMapper.findByQuotation(quotationId, ctx.getTenantId());
    }

    /** 创建首版报价单。 */
    @Transactional
    public Quotation create(Long opportunityId, List<QuotationItemDto> items, LocalDate validUntil, String terms) {
        UserContext ctx = requireContext();
        if (opportunityId == null) throw new ServiceException(400, "opportunityId is required");
        if (items == null || items.isEmpty()) {
            throw new ServiceException(400, "items must not be empty");
        }
        // 校验 opportunity 存在
        opportunityService.get(opportunityId);

        Quotation q = new Quotation();
        q.setCode("Q-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        q.setOpportunityId(opportunityId);
        q.setVersion(1);
        q.setValidUntil(validUntil);
        q.setTerms(terms);
        q.setStatus(STATUS_DRAFT);
        q.setTenantId(ctx.getTenantId());
        q.setTotalAmount(BigDecimal.ZERO);
        try {
            quotationMapper.insert(q);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Quotation code conflict", ex);
        }
        saveItems(q.getId(), items, ctx.getTenantId());
        q.setTotalAmount(calcTotal(q.getId(), ctx.getTenantId()));
        quotationMapper.updateById(q);
        return q;
    }

    /**
     * 基于已有 opportunity 创建新版本 — version = MAX(version)+1;复制上一版 items。
     * 校验 opportunityId 一致。
     */
    @Transactional
    public Quotation createVersion(Long opportunityId) {
        UserContext ctx = requireContext();
        if (opportunityId == null) throw new ServiceException(400, "opportunityId is required");
        Quotation latest = quotationMapper.findLatestByOpportunity(opportunityId, ctx.getTenantId());
        if (latest == null) {
            throw new ServiceException(404, "No prior quotation for opportunity: " + opportunityId);
        }
        if (!STATUS_REJECTED.equals(latest.getStatus())
            && !STATUS_EXPIRED.equals(latest.getStatus())
            && !STATUS_DRAFT.equals(latest.getStatus())) {
            // After accept/sent the version lineage should still allow a new revision.
            // We don't forbid — caller may be re-negotiating.
        }
        int nextVersion = latest.getVersion() == null ? 2 : latest.getVersion() + 1;
        Quotation q = new Quotation();
        q.setCode("Q-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        q.setOpportunityId(opportunityId);
        q.setVersion(nextVersion);
        q.setValidUntil(latest.getValidUntil());
        q.setTerms(latest.getTerms());
        q.setStatus(STATUS_DRAFT);
        q.setTenantId(ctx.getTenantId());
        q.setTotalAmount(BigDecimal.ZERO);
        try {
            quotationMapper.insert(q);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Quotation code conflict", ex);
        }
        // 复制上一版 items
        List<QuotationItem> prev = quotationItemMapper.findByQuotation(latest.getId(), ctx.getTenantId());
        for (QuotationItem src : prev) {
            QuotationItem copy = new QuotationItem();
            copy.setQuotationId(q.getId());
            copy.setItemName(src.getItemName());
            copy.setSku(src.getSku());
            copy.setQuantity(src.getQuantity());
            copy.setUnitPrice(src.getUnitPrice());
            copy.setSubtotal(src.getSubtotal());
            copy.setTenantId(ctx.getTenantId());
            quotationItemMapper.insert(copy);
        }
        q.setTotalAmount(calcTotal(q.getId(), ctx.getTenantId()));
        quotationMapper.updateById(q);
        log.info("Quotation new version {} for opportunity {} (prev version {})",
            q.getVersion(), opportunityId, latest.getVersion());
        return q;
    }

    @Transactional
    public Quotation send(Long id) {
        Quotation q = get(id);
        if (!STATUS_DRAFT.equals(q.getStatus())) {
            throw new ServiceException(409, "Only draft quotation can be sent: " + q.getStatus());
        }
        q.setStatus(STATUS_SENT);
        quotationMapper.updateById(q);
        return q;
    }

    /**
     * 报价被接受 — 创建对应 Order(status=draft, source=quotation, sourceId=q.id)。
     */
    @Transactional
    public Order accept(Long id) {
        UserContext ctx = requireContext();
        Quotation q = get(id);
        if (!STATUS_SENT.equals(q.getStatus())) {
            throw new ServiceException(409, "Only sent quotation can be accepted: " + q.getStatus());
        }
        OpportunityService opportunity = opportunityService;
        com.lumen.sales.entity.Opportunity opp = opportunity.get(q.getOpportunityId());
        Order o = new Order();
        o.setCode("O-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        o.setCustomerId(opp.getCustomerId());
        o.setContractId(null); // TODO P5: 关联 contract
        o.setSourceType("quotation");
        o.setSourceId(q.getId());
        o.setTotalAmount(q.getTotalAmount());
        o.setOrderDate(LocalDate.now());
        o.setStatus(OrderService.STATUS_DRAFT);
        o.setTenantId(ctx.getTenantId());
        orderMapper.insert(o);
        q.setStatus(STATUS_ACCEPTED);
        quotationMapper.updateById(q);
        log.info("Quotation {} accepted → Order {}", id, o.getId());
        return o;
    }

    @Transactional
    public Quotation reject(Long id, String reason) {
        Quotation q = get(id);
        if (!STATUS_SENT.equals(q.getStatus())) {
            throw new ServiceException(409, "Only sent quotation can be rejected: " + q.getStatus());
        }
        q.setStatus(STATUS_REJECTED);
        quotationMapper.updateById(q);
        log.info("Quotation {} rejected: {}", id, reason);
        return q;
    }

    private void saveItems(Long quotationId, List<QuotationItemDto> items, Long tenantId) {
        for (QuotationItemDto dto : items) {
            if (dto.getItemName() == null || dto.getItemName().isBlank()) {
                throw new ServiceException(400, "itemName is required");
            }
            if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
                throw new ServiceException(400, "quantity must be > 0");
            }
            if (dto.getUnitPrice() == null || dto.getUnitPrice().signum() <= 0) {
                throw new ServiceException(400, "unitPrice must be > 0");
            }
            QuotationItem it = new QuotationItem();
            it.setQuotationId(quotationId);
            it.setItemName(dto.getItemName());
            it.setSku(dto.getSku());
            it.setQuantity(dto.getQuantity());
            it.setUnitPrice(dto.getUnitPrice());
            it.setSubtotal(dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
            it.setTenantId(tenantId);
            quotationItemMapper.insert(it);
        }
    }

    private BigDecimal calcTotal(Long quotationId, Long tenantId) {
        BigDecimal total = BigDecimal.ZERO;
        List<QuotationItem> items = new ArrayList<>(quotationItemMapper.findByQuotation(quotationId, tenantId));
        for (QuotationItem it : items) {
            if (it.getSubtotal() != null) total = total.add(it.getSubtotal());
        }
        return total;
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }
}