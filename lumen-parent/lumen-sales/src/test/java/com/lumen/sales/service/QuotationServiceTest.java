package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.dto.QuotationItemDto;
import com.lumen.sales.entity.Opportunity;
import com.lumen.sales.entity.Order;
import com.lumen.sales.entity.Quotation;
import com.lumen.sales.entity.QuotationItem;
import com.lumen.sales.mapper.OrderMapper;
import com.lumen.sales.mapper.QuotationItemMapper;
import com.lumen.sales.mapper.QuotationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报价版本号递增 + accept 创建订单。
 */
@ExtendWith(MockitoExtension.class)
class QuotationServiceTest {

    @Mock private QuotationMapper quotationMapper;
    @Mock private QuotationItemMapper quotationItemMapper;
    @Mock private OpportunityService opportunityService;
    @Mock private OrderMapper orderMapper;
    @Mock private CustomerService customerService;

    @InjectMocks private QuotationService quotationService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long OPP_ID = 99L;
    private static final long QUOT_ID = 88L;
    private static final long CUSTOMER_ID = 77L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("sales")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Quotation stubQuotation(int version, String status) {
        Quotation q = new Quotation();
        q.setId(QUOT_ID);
        q.setCode("Q-001");
        q.setOpportunityId(OPP_ID);
        q.setVersion(version);
        q.setStatus(status);
        q.setTotalAmount(new BigDecimal("1000"));
        q.setTenantId(TENANT);
        return q;
    }

    private Opportunity stubOpp() {
        Opportunity o = new Opportunity();
        o.setId(OPP_ID);
        o.setCustomerId(CUSTOMER_ID);
        o.setTenantId(TENANT);
        return o;
    }

    private QuotationItem stubItem(String name, int qty, String price) {
        QuotationItem i = new QuotationItem();
        i.setItemName(name);
        i.setQuantity(qty);
        i.setUnitPrice(new BigDecimal(price));
        i.setSubtotal(new BigDecimal(price).multiply(BigDecimal.valueOf(qty)));
        i.setTenantId(TENANT);
        return i;
    }

    private QuotationItemDto stubDto() {
        QuotationItemDto d = new QuotationItemDto();
        d.setItemName("Widget");
        d.setQuantity(2);
        d.setUnitPrice(new BigDecimal("100"));
        return d;
    }

    @Test
    void createVersion_incrementsVersionAndCopiesItems() {
        Quotation prev = stubQuotation(3, QuotationService.STATUS_REJECTED);
        when(quotationMapper.findLatestByOpportunity(OPP_ID, TENANT)).thenReturn(prev);
        when(quotationMapper.insert(any(Quotation.class))).thenAnswer(inv -> {
            Quotation arg = inv.getArgument(0);
            arg.setId(QUOT_ID + 1);
            return 1;
        });
        when(quotationItemMapper.findByQuotation(eq(QUOT_ID), eq(TENANT)))
            .thenReturn(List.of(stubItem("Widget", 2, "100")));

        Quotation out = quotationService.createVersion(OPP_ID);
        assertEquals(4, out.getVersion());

        // 必须复制了上一版的 items
        ArgumentCaptor<QuotationItem> captor = ArgumentCaptor.forClass(QuotationItem.class);
        verify(quotationItemMapper, atLeastOnce()).insert(captor.capture());
        List<QuotationItem> inserted = captor.getAllValues();
        assertTrue(inserted.stream().anyMatch(
            i -> "Widget".equals(i.getItemName()) && i.getQuantity() == 2));
    }

    @Test
    void createVersion_noPrior_throws404() {
        when(quotationMapper.findLatestByOpportunity(OPP_ID, TENANT)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> quotationService.createVersion(OPP_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    void accept_sentQuotationCreatesOrder() {
        Quotation q = stubQuotation(1, QuotationService.STATUS_SENT);
        Opportunity opp = stubOpp();
        when(quotationMapper.selectById(QUOT_ID)).thenReturn(q);
        when(opportunityService.get(OPP_ID)).thenReturn(opp);
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            Order arg = inv.getArgument(0);
            arg.setId(500L);
            return 1;
        });

        Order o = quotationService.accept(QUOT_ID);
        assertEquals(CUSTOMER_ID, o.getCustomerId());
        assertEquals("quotation", o.getSourceType());
        assertEquals(QUOT_ID, o.getSourceId());
        assertEquals(OrderService.STATUS_DRAFT, o.getStatus());
    }

    @Test
    void accept_draftQuotation_throws409() {
        Quotation q = stubQuotation(1, QuotationService.STATUS_DRAFT);
        when(quotationMapper.selectById(QUOT_ID)).thenReturn(q);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> quotationService.accept(QUOT_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void send_draftQuotation_flipsToSent() {
        Quotation q = stubQuotation(1, QuotationService.STATUS_DRAFT);
        when(quotationMapper.selectById(QUOT_ID)).thenReturn(q);
        when(quotationMapper.updateById(any(Quotation.class))).thenAnswer(inv -> 1);
        Quotation out = quotationService.send(QUOT_ID);
        assertEquals(QuotationService.STATUS_SENT, out.getStatus());
    }

    @Test
    void send_alreadySent_throws409() {
        Quotation q = stubQuotation(1, QuotationService.STATUS_SENT);
        when(quotationMapper.selectById(QUOT_ID)).thenReturn(q);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> quotationService.send(QUOT_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void create_calculatesTotalAmount() {
        when(opportunityService.get(any())).thenReturn(stubOpp());
        when(quotationMapper.insert(any(Quotation.class))).thenAnswer(inv -> {
            Quotation arg = inv.getArgument(0);
            arg.setId(QUOT_ID);
            return 1;
        });
        when(quotationItemMapper.findByQuotation(eq(QUOT_ID), eq(TENANT)))
            .thenReturn(List.of(stubItem("Widget", 2, "100")));

        Quotation out = quotationService.create(OPP_ID, List.of(stubDto()), null, null);
        assertEquals(QUOT_ID, out.getId());
        assertEquals(0, new BigDecimal("200.00").compareTo(out.getTotalAmount()));
    }

    @Test
    void create_emptyItems_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> quotationService.create(OPP_ID, List.of(), null, null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void create_nullOpportunity_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> quotationService.create(null, List.of(stubDto()), null, null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void reject_sentQuotation_flipsToRejected() {
        Quotation q = stubQuotation(1, QuotationService.STATUS_SENT);
        when(quotationMapper.selectById(QUOT_ID)).thenReturn(q);
        when(quotationMapper.updateById(any(Quotation.class))).thenAnswer(inv -> 1);
        Quotation out = quotationService.reject(QUOT_ID, "price too high");
        assertEquals(QuotationService.STATUS_REJECTED, out.getStatus());
    }
}