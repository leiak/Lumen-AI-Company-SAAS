package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.dto.SaveInvoiceRequest;
import com.lumen.finance.entity.FinInvoice;
import com.lumen.finance.mapper.FinInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 发票 service。敏感字段 (buyer/seller/tax_no) 透明加解密;
 * 解密仅在 finance_admin 通过 {@link #sensitiveInfo(Long)} 访问时返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RECOGNIZED = "recognized";
    public static final String STATUS_CERTIFIED = "certified";
    /** Roles allowed to see the plaintext sensitive fields. */
    public static final String SUPER_ADMIN_ROLE = "super_admin";
    public static final String FINANCE_ADMIN_ROLE = "finance_admin";

    private final FinInvoiceMapper invoiceMapper;

    public FinInvoice get(Long id) {
        UserContext ctx = requireUserContext();
        FinInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) throw new ServiceException(404, "Invoice not found: " + id);
        assertTenant(inv, ctx);
        return inv;
    }

    public List<FinInvoice> findByTaxNoAndPeriod(String taxNo, String period) {
        requireTenant();
        return invoiceMapper.findByTaxNoAndPeriod(taxNo, period);
    }

    /**
     * 保存 (含敏感字段加密写入)。
     */
    @Transactional
    public FinInvoice save(SaveInvoiceRequest req) {
        UserContext ctx = requireUserContext();
        FinInvoice inv = new FinInvoice();
        inv.setTenantId(ctx.getTenantId());
        inv.setInvoiceNo(req.getInvoiceNo());
        inv.setInvoiceType(req.getInvoiceType());
        inv.setAmount(req.getAmount());
        inv.setTaxAmount(req.getTaxAmount());
        inv.setIssueDate(req.getIssueDate());
        inv.setSourceType(req.getSourceType());
        inv.setSourceId(req.getSourceId());
        inv.setRecognizeStatus(STATUS_PENDING);
        // 敏感字段明文传入, typeHandler 加密落库
        inv.setBuyerNameEnc(req.getBuyerName());
        inv.setSellerNameEnc(req.getSellerName());
        inv.setTaxNoEnc(req.getTaxNo());
        invoiceMapper.insert(inv);
        log.info("Invoice saved id={} no={} type={}", inv.getId(), inv.getInvoiceNo(), inv.getInvoiceType());
        return inv;
    }

    /**
     * 勾选认证: pending -> recognized。
     */
    @Transactional
    public FinInvoice recognize(Long id) {
        FinInvoice inv = get(id);
        if (!STATUS_PENDING.equals(inv.getRecognizeStatus())) {
            throw new ServiceException(409,
                "Only pending invoices can be recognized; status=" + inv.getRecognizeStatus());
        }
        inv.setRecognizeStatus(STATUS_RECOGNIZED);
        invoiceMapper.updateById(inv);
        log.info("Invoice recognized id={}", inv.getId());
        return inv;
    }

    /**
     * 认证完成: recognized -> certified。
     */
    @Transactional
    public FinInvoice certify(Long id) {
        FinInvoice inv = get(id);
        if (!STATUS_RECOGNIZED.equals(inv.getRecognizeStatus())) {
            throw new ServiceException(409,
                "Only recognized invoices can be certified; status=" + inv.getRecognizeStatus());
        }
        inv.setRecognizeStatus(STATUS_CERTIFIED);
        invoiceMapper.updateById(inv);
        log.info("Invoice certified id={}", inv.getId());
        return inv;
    }

    /**
     * 返回解密后的敏感信息。仅 finance_admin / super_admin 可访问。
     */
    public Map<String, String> sensitiveInfo(Long id) {
        UserContext ctx = requireUserContext();
        boolean allowed = ctx.getRoles() != null
            && (ctx.getRoles().contains(FINANCE_ADMIN_ROLE)
                || ctx.getRoles().contains(SUPER_ADMIN_ROLE));
        if (!allowed) {
            throw new ServiceException(403, "Only finance_admin can view sensitive invoice info");
        }
        FinInvoice inv = get(id); // tenant check
        return Map.of(
            "buyerName", inv.getBuyerNameEnc() == null ? "" : inv.getBuyerNameEnc(),
            "sellerName", inv.getSellerNameEnc() == null ? "" : inv.getSellerNameEnc(),
            "taxNo", inv.getTaxNoEnc() == null ? "" : inv.getTaxNoEnc()
        );
    }

    // ---------- helpers ----------
    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    private UserContext requireTenant() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return ctx;
    }

    private void assertTenant(FinInvoice inv, UserContext ctx) {
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(inv.getTenantId())) {
            throw new ServiceException(404, "Invoice not found: " + inv.getId());
        }
    }
}
