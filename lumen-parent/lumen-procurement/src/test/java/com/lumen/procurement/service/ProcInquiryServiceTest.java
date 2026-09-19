package com.lumen.procurement.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.AwardInquiryRequest;
import com.lumen.procurement.dto.QuotationSummaryDto;
import com.lumen.procurement.entity.ProcInquiry;
import com.lumen.procurement.entity.ProcQuotation;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.mapper.ProcInquiryMapper;
import com.lumen.procurement.mapper.ProcOrderItemMapper;
import com.lumen.procurement.mapper.ProcOrderMapper;
import com.lumen.procurement.mapper.ProcQuotationMapper;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcInquiryServiceTest {

    @Mock private ProcInquiryMapper inquiryMapper;
    @Mock private ProcQuotationMapper quotationMapper;
    @Mock private ProcSupplierMapper supplierMapper;
    @Mock private ProcOrderMapper orderMapper;
    @Mock private ProcOrderItemMapper orderItemMapper;
    @InjectMocks private ProcInquiryService inquiryService;

    private static final long TENANT = 1L;
    private static final long INQUIRY_ID = 100L;
    private static final long SUPPLIER_A = 1L;
    private static final long SUPPLIER_B = 2L;
    private static final long SUPPLIER_C = 3L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(10L).tenantId(TENANT).userName("alice")
            .roles(Set.of("procurement_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private ProcInquiry inquiry(String status) {
        ProcInquiry i = new ProcInquiry();
        i.setId(INQUIRY_ID);
        i.setCode("INQ-001");
        i.setTitle("Test inquiry");
        i.setStatus(status);
        i.setInquiryDate(LocalDate.now());
        i.setDeadline(LocalDate.now().plusDays(7));
        i.setTenantId(TENANT);
        return i;
    }

    private ProcSupplier supplier(long id, String name, String status, String rating) {
        ProcSupplier s = new ProcSupplier();
        s.setId(id);
        s.setName(name);
        s.setStatus(status);
        s.setRating(new BigDecimal(rating));
        s.setTenantId(TENANT);
        return s;
    }

    private ProcQuotation quote(long id, long supplierId, BigDecimal amount, int leadTime, String status) {
        ProcQuotation q = new ProcQuotation();
        q.setId(id);
        q.setInquiryId(INQUIRY_ID);
        q.setSupplierId(supplierId);
        q.setTotalAmount(amount);
        q.setLeadTimeDays(leadTime);
        q.setStatus(status);
        q.setTenantId(TENANT);
        return q;
    }

    // 15. award 必须选 status=submitted 的 quotation
    @Test
    void award_selectingAlreadySelected_throws409() {
        ProcInquiry i = inquiry("published");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        ProcQuotation q = quote(50L, SUPPLIER_A, new BigDecimal("100"), 5, "selected");
        when(quotationMapper.selectById(50L)).thenReturn(q);
        AwardInquiryRequest req = new AwardInquiryRequest();
        req.setSelectedQuotationId(50L);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> inquiryService.award(INQUIRY_ID, req));
        assertEquals(409, ex.getCode());
    }

    // 16. award 跨 inquiry 的 quotation
    @Test
    void award_quotationFromOtherInquiry_throws409() {
        ProcInquiry i = inquiry("published");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        ProcQuotation q = quote(50L, SUPPLIER_A, new BigDecimal("100"), 5, "submitted");
        q.setInquiryId(999L);
        when(quotationMapper.selectById(50L)).thenReturn(q);
        AwardInquiryRequest req = new AwardInquiryRequest();
        req.setSelectedQuotationId(50L);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> inquiryService.award(INQUIRY_ID, req));
        assertEquals(409, ex.getCode());
    }

    // 17. publish 黑名单拒绝
    @Test
    void publish_blacklistSupplier_throws409() {
        ProcInquiry i = inquiry("draft");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        ProcSupplier s = supplier(SUPPLIER_A, "BadCo", "blacklist", "0.00");
        when(supplierMapper.selectById(SUPPLIER_A)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> inquiryService.publish(INQUIRY_ID, List.of(SUPPLIER_A)));
        assertEquals(409, ex.getCode());
    }

    // 18. publish 空 supplierIds 拒绝
    @Test
    void publish_emptySupplierIds_throws400() {
        ProcInquiry i = inquiry("draft");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> inquiryService.publish(INQUIRY_ID, List.of()));
        assertEquals(400, ex.getCode());
    }

    // 19. award 成功 → 其它报价 rejected
    @Test
    void award_succeeds_marksOtherRejected() {
        ProcInquiry i = inquiry("published");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        ProcQuotation selected = quote(50L, SUPPLIER_A, new BigDecimal("100"), 5, "submitted");
        when(quotationMapper.selectById(50L)).thenReturn(selected);
        ProcQuotation other = quote(51L, SUPPLIER_B, new BigDecimal("110"), 6, "submitted");
        when(quotationMapper.findByInquiry(eq(TENANT), eq(INQUIRY_ID)))
            .thenReturn(new ArrayList<>(List.of(selected, other)));

        AwardInquiryRequest req = new AwardInquiryRequest();
        req.setSelectedQuotationId(50L);
        ProcInquiry awarded = inquiryService.award(INQUIRY_ID, req);
        assertEquals("awarded", awarded.getStatus());
        assertEquals("selected", selected.getStatus());
        assertEquals("rejected", other.getStatus());
    }

    // 20. compareQuote 综合分计算
    @Test
    void compareQuote_returnsSortedByCompositeScore() {
        ProcInquiry i = inquiry("published");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        ProcQuotation q1 = quote(11L, SUPPLIER_A, new BigDecimal("100"), 5, "submitted");
        ProcQuotation q2 = quote(12L, SUPPLIER_B, new BigDecimal("110"), 6, "submitted");
        ProcQuotation q3 = quote(13L, SUPPLIER_C, new BigDecimal("120"), 7, "submitted");
        when(quotationMapper.findByInquiry(eq(TENANT), eq(INQUIRY_ID)))
            .thenReturn(new ArrayList<>(List.of(q1, q2, q3)));
        when(supplierMapper.selectById(SUPPLIER_A)).thenReturn(supplier(SUPPLIER_A, "A", "active", "4.0"));
        when(supplierMapper.selectById(SUPPLIER_B)).thenReturn(supplier(SUPPLIER_B, "B", "active", "3.0"));
        when(supplierMapper.selectById(SUPPLIER_C)).thenReturn(supplier(SUPPLIER_C, "C", "active", "2.0"));

        List<QuotationSummaryDto> result = inquiryService.compareQuote(INQUIRY_ID);
        assertEquals(3, result.size());
        // 综合分最高者排第一 — 最低价 4.0 评级 → 0.7*1 + 0.3*0.8 = 0.94
        QuotationSummaryDto first = result.get(0);
        assertTrue(first.isSelected());
        // 第一名应该是 最低价 100 (A 供应商)
        assertEquals(0, first.getTotalAmount().compareTo(new BigDecimal("100")));
        // 综合分单调递减
        for (int k = 0; k < result.size() - 1; k++) {
            assertTrue(result.get(k).getCompositeScore() >= result.get(k + 1).getCompositeScore(),
                "Composite score must be monotonically decreasing");
        }
    }

    // 21. compareQuote 仅 submitted 参与评估
    @Test
    void compareQuote_onlySubmittedParticipate() {
        ProcInquiry i = inquiry("published");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        ProcQuotation submitted = quote(11L, SUPPLIER_A, new BigDecimal("100"), 5, "submitted");
        ProcQuotation rejected = quote(12L, SUPPLIER_B, new BigDecimal("110"), 6, "rejected");
        when(quotationMapper.findByInquiry(eq(TENANT), eq(INQUIRY_ID)))
            .thenReturn(new ArrayList<>(List.of(submitted, rejected)));
        when(supplierMapper.selectById(SUPPLIER_A)).thenReturn(supplier(SUPPLIER_A, "A", "active", "4.0"));

        List<QuotationSummaryDto> result = inquiryService.compareQuote(INQUIRY_ID);
        assertEquals(1, result.size());
    }

    // 22. compareQuote 空集返回空列表
    @Test
    void compareQuote_emptyQuotations_returnsEmpty() {
        ProcInquiry i = inquiry("published");
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(i);
        when(quotationMapper.findByInquiry(eq(TENANT), eq(INQUIRY_ID)))
            .thenReturn(new ArrayList<>());
        List<QuotationSummaryDto> result = inquiryService.compareQuote(INQUIRY_ID);
        assertTrue(result.isEmpty());
    }
}