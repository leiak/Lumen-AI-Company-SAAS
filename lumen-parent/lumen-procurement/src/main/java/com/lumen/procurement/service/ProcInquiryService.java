package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.AwardInquiryRequest;
import com.lumen.procurement.dto.CreateInquiryRequest;
import com.lumen.procurement.dto.QuotationDto;
import com.lumen.procurement.dto.QuotationSummaryDto;
import com.lumen.procurement.entity.ProcInquiry;
import com.lumen.procurement.entity.ProcOrder;
import com.lumen.procurement.entity.ProcOrderItem;
import com.lumen.procurement.entity.ProcQuotation;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.mapper.ProcInquiryMapper;
import com.lumen.procurement.mapper.ProcOrderItemMapper;
import com.lumen.procurement.mapper.ProcOrderMapper;
import com.lumen.procurement.mapper.ProcQuotationMapper;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 询价单询比价主流程。
 *
 * <p>状态机: draft → published → closed / awarded。</p>
 * <p>安全要点:</p>
 * <ul>
 *   <li>publish: 仅 draft 可发布 → published, 校验至少 1 个 supplier (itemIds)。</li>
 *   <li>award: 只能选 status=submitted 的 quotation。</li>
 *   <li>黑名单供应商不能再被选入。</li>
 *   <li>compareQuote: 综合分 = 0.7 × 价格分 + 0.3 × 评级分。</li>
 *   <li>createOrderFromQuotation: quotation 必须是 selected 状态。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcInquiryService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_CLOSED = "closed";
    public static final String STATUS_AWARDED = "awarded";

    public static final String QUOTATION_SUBMITTED = "submitted";
    public static final String QUOTATION_SELECTED = "selected";
    public static final String QUOTATION_REJECTED = "rejected";
    public static final String QUOTATION_WITHDRAWN = "withdrawn";

    public static final String ORDER_SOURCE_QUOTATION = "quotation";

    private final ProcInquiryMapper inquiryMapper;
    private final ProcQuotationMapper quotationMapper;
    private final ProcSupplierMapper supplierMapper;
    private final ProcOrderMapper orderMapper;
    private final ProcOrderItemMapper orderItemMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<ProcInquiry> page(int pageNum, int pageSize, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<ProcInquiry>().orderByDesc(ProcInquiry::getId);
        if (status != null && !status.isBlank()) w.eq(ProcInquiry::getStatus, status);
        return inquiryMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ProcInquiry getById(Long id) {
        UserContext ctx = requireCtx();
        ProcInquiry i = inquiryMapper.selectById(id);
        if (i == null) throw new ServiceException(404, "Inquiry not found: " + id);
        if (!i.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Inquiry not found: " + id);
        }
        return i;
    }

    @Transactional
    public ProcInquiry create(CreateInquiryRequest req) {
        UserContext ctx = requireCtx();
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (inquiryMapper.findByCode(ctx.getTenantId(), req.getCode()) != null) {
            throw new ServiceException(409, "Inquiry code already exists: " + req.getCode());
        }
        ProcInquiry i = new ProcInquiry();
        i.setTenantId(ctx.getTenantId());
        i.setCode(req.getCode());
        i.setTitle(req.getTitle());
        i.setInquiryDate(req.getInquiryDate());
        i.setDeadline(req.getDeadline());
        i.setStatus(STATUS_DRAFT);
        i.setCreatorId(ctx.getUserId());
        try {
            inquiryMapper.insert(i);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Inquiry code conflict", ex);
        }
        log.info("Inquiry created id={} code={}", i.getId(), i.getCode());
        return i;
    }

    /**
     * 发布询价单: draft → published。
     * 必须先有 itemIds (受邀供应商列表) 且所有 supplier 都不能是 blacklist。
     */
    @Transactional
    public ProcInquiry publish(Long id, List<Long> supplierIds) {
        ProcInquiry i = getById(id);
        if (!STATUS_DRAFT.equals(i.getStatus())) {
            throw new ServiceException(409, "Only draft inquiry can be published (current=" + i.getStatus() + ")");
        }
        if (supplierIds == null || supplierIds.isEmpty()) {
            throw new ServiceException(400, "supplierIds must not be empty when publishing");
        }
        // 校验供应商: 存在 + 同租户 + 非黑名单
        for (Long sid : supplierIds) {
            ProcSupplier s = supplierMapper.selectById(sid);
            if (s == null || !s.getTenantId().equals(i.getTenantId())) {
                throw new ServiceException(400, "Invalid supplierId: " + sid);
            }
            if (ProcSupplierService.STATUS_BLACKLIST.equals(s.getStatus())) {
                throw new ServiceException(409, "Supplier is blacklisted: " + sid);
            }
        }
        i.setStatus(STATUS_PUBLISHED);
        i.setPublishedAt(LocalDateTime.now());
        inquiryMapper.updateById(i);
        log.info("Inquiry published id={} suppliers={}", id, supplierIds.size());
        return i;
    }

    /**
     * 提交报价 (一个 supplier 对一个 inquiry 只能有一条报价 — UNIQUE 约束)。
     */
    @Transactional
    public ProcQuotation submitQuotation(Long inquiryId, QuotationDto dto) {
        UserContext ctx = requireCtx();
        ProcInquiry i = getById(inquiryId);
        if (!STATUS_PUBLISHED.equals(i.getStatus())) {
            throw new ServiceException(409, "Inquiry not published (current=" + i.getStatus() + ")");
        }
        ProcSupplier s = supplierMapper.selectById(dto.getSupplierId());
        if (s == null || !s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(400, "Invalid supplierId");
        }
        if (ProcSupplierService.STATUS_BLACKLIST.equals(s.getStatus())) {
            throw new ServiceException(409, "Supplier is blacklisted");
        }
        // 同一 inquiry + supplier 仅一条
        List<ProcQuotation> existing = quotationMapper.findBySupplier(ctx.getTenantId(), dto.getSupplierId());
        for (ProcQuotation q : existing) {
            if (q.getInquiryId().equals(inquiryId)) {
                throw new ServiceException(409, "Supplier already submitted quotation for this inquiry");
            }
        }
        ProcQuotation q = new ProcQuotation();
        q.setTenantId(ctx.getTenantId());
        q.setInquiryId(inquiryId);
        q.setSupplierId(dto.getSupplierId());
        q.setTotalAmount(dto.getTotalAmount());
        q.setValidUntil(dto.getValidUntil());
        q.setLeadTimeDays(dto.getLeadTimeDays());
        q.setPaymentTerms(dto.getPaymentTerms());
        q.setStatus(QUOTATION_SUBMITTED);
        try {
            quotationMapper.insert(q);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Duplicate quotation", ex);
        }
        log.info("Quotation submitted id={} inquiry={} supplier={}", q.getId(), inquiryId, dto.getSupplierId());
        return q;
    }

    /**
     * 比价分析: 按 amount(70%) + rating(30%) 综合分排序。
     * 综合分公式:
     *   priceScore = (maxAmount - amount) / (maxAmount - minAmount)  // 1=最低价,0=最高价
     *   ratingScore = rating / 5
     *   compositeScore = 0.7 * priceScore + 0.3 * ratingScore
     * 仅 status=submitted 的报价参与评估。
     */
    public List<QuotationSummaryDto> compareQuote(Long inquiryId) {
        UserContext ctx = requireCtx();
        getById(inquiryId);
        List<ProcQuotation> all = quotationMapper.findByInquiry(ctx.getTenantId(), inquiryId);
        List<ProcQuotation> candidates = new ArrayList<>();
        for (ProcQuotation q : all) {
            if (QUOTATION_SUBMITTED.equals(q.getStatus())) {
                candidates.add(q);
            }
        }
        if (candidates.isEmpty()) return List.of();
        BigDecimal minAmount = candidates.stream()
            .map(ProcQuotation::getTotalAmount)
            .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxAmount = candidates.stream()
            .map(ProcQuotation::getTotalAmount)
            .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal range = maxAmount.subtract(minAmount);
        List<QuotationSummaryDto> out = new ArrayList<>();
        for (ProcQuotation q : candidates) {
            ProcSupplier s = supplierMapper.selectById(q.getSupplierId());
            BigDecimal rating = s == null || s.getRating() == null ? BigDecimal.ZERO : s.getRating();
            double priceScore;
            if (range.signum() == 0) {
                priceScore = 1.0;  // 所有报价一致 → 都满分
            } else {
                priceScore = maxAmount.subtract(q.getTotalAmount())
                    .divide(range, 8, RoundingMode.HALF_UP).doubleValue();
            }
            double ratingScore = rating.divide(new BigDecimal("5"), 8, RoundingMode.HALF_UP).doubleValue();
            double composite = 0.7 * priceScore + 0.3 * ratingScore;
            BigDecimal overRate = minAmount.signum() == 0
                ? BigDecimal.ZERO
                : q.getTotalAmount().subtract(minAmount)
                    .divide(minAmount, 4, RoundingMode.HALF_UP);
            out.add(new QuotationSummaryDto(
                q.getId(), q.getSupplierId(),
                s == null ? "?" : s.getName(),
                q.getTotalAmount(),
                q.getLeadTimeDays(),
                rating,
                composite,
                overRate,
                false));
        }
        out.sort((x, y) -> Double.compare(y.getCompositeScore(), x.getCompositeScore()));
        if (!out.isEmpty()) out.get(0).setSelected(true);
        return out;
    }

    /**
     * 中标: 选择 submitted 的 quotation → inquiry 关闭, 其余 rejected。
     */
    @Transactional
    public ProcInquiry award(Long inquiryId, AwardInquiryRequest req) {
        UserContext ctx = requireCtx();
        ProcInquiry i = getById(inquiryId);
        if (!STATUS_PUBLISHED.equals(i.getStatus())) {
            throw new ServiceException(409, "Only published inquiry can be awarded (current=" + i.getStatus() + ")");
        }
        if (req.getSelectedQuotationId() == null) {
            throw new ServiceException(400, "selectedQuotationId is required");
        }
        ProcQuotation selected = quotationMapper.selectById(req.getSelectedQuotationId());
        if (selected == null || !selected.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Quotation not found");
        }
        if (!selected.getInquiryId().equals(inquiryId)) {
            throw new ServiceException(409, "Quotation does not belong to this inquiry");
        }
        if (!QUOTATION_SUBMITTED.equals(selected.getStatus())) {
            throw new ServiceException(409, "Only submitted quotation can be awarded (current=" + selected.getStatus() + ")");
        }
        // 标记选中
        selected.setStatus(QUOTATION_SELECTED);
        quotationMapper.updateById(selected);
        // 其他报价标记 rejected
        List<ProcQuotation> others = quotationMapper.findByInquiry(ctx.getTenantId(), inquiryId);
        for (ProcQuotation o : others) {
            if (!o.getId().equals(selected.getId()) && QUOTATION_SUBMITTED.equals(o.getStatus())) {
                o.setStatus(QUOTATION_REJECTED);
                quotationMapper.updateById(o);
            }
        }
        // inquiry 状态
        i.setStatus(STATUS_AWARDED);
        inquiryMapper.updateById(i);
        log.info("Inquiry awarded id={} selectedQuotation={}", inquiryId, selected.getId());
        return i;
    }

    /**
     * 关闭询价单 (不发报价) → closed。
     */
    @Transactional
    public ProcInquiry close(Long inquiryId) {
        ProcInquiry i = getById(inquiryId);
        if (STATUS_AWARDED.equals(i.getStatus()) || STATUS_CLOSED.equals(i.getStatus())) {
            throw new ServiceException(409, "Inquiry already finalized (current=" + i.getStatus() + ")");
        }
        i.setStatus(STATUS_CLOSED);
        inquiryMapper.updateById(i);
        return i;
    }

    /**
     * 从中标的 quotation 直接创建采购单 (draft)。
     * quotation 必须 status=selected。
     */
    @Transactional
    public ProcOrder createOrderFromQuotation(Long quotationId, String orderCode, java.time.LocalDate orderDate,
                                                java.time.LocalDate expectedDeliveryAt) {
        UserContext ctx = requireCtx();
        ProcQuotation q = quotationMapper.selectById(quotationId);
        if (q == null || !q.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Quotation not found");
        }
        if (!QUOTATION_SELECTED.equals(q.getStatus())) {
            throw new ServiceException(409, "Only selected quotation can create order (current=" + q.getStatus() + ")");
        }
        if (orderCode == null || orderCode.isBlank()) {
            throw new ServiceException(400, "orderCode is required");
        }
        if (orderMapper.findByCode(ctx.getTenantId(), orderCode) != null) {
            throw new ServiceException(409, "Order code already exists: " + orderCode);
        }
        ProcOrder order = new ProcOrder();
        order.setTenantId(ctx.getTenantId());
        order.setCode(orderCode);
        order.setSupplierId(q.getSupplierId());
        order.setSourceType(ORDER_SOURCE_QUOTATION);
        order.setSourceId(quotationId);
        order.setTotalAmount(q.getTotalAmount());
        order.setOrderDate(orderDate);
        order.setExpectedDeliveryAt(expectedDeliveryAt);
        order.setStatus(ProcOrderService.STATUS_DRAFT);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Order code conflict", ex);
        }
        log.info("Order created from quotation orderId={} quotationId={}", order.getId(), quotationId);
        return order;
    }
}