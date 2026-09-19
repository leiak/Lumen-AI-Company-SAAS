package com.lumen.procurement.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.BiddingEvaluationDto;
import com.lumen.procurement.entity.ProcBidding;
import com.lumen.procurement.entity.ProcBiddingParticipant;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.mapper.ProcBiddingMapper;
import com.lumen.procurement.mapper.ProcBiddingParticipantMapper;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcBiddingServiceTest {

    @Mock private ProcBiddingMapper biddingMapper;
    @Mock private ProcBiddingParticipantMapper participantMapper;
    @Mock private ProcSupplierMapper supplierMapper;
    @InjectMocks private ProcBiddingService biddingService;

    private static final long TENANT = 1L;
    private static final long BIDDING_ID = 100L;
    private static final long SUPPLIER_A = 1L;
    private static final long SUPPLIER_B = 2L;

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

    private ProcBidding bidding(String status) {
        ProcBidding b = new ProcBidding();
        b.setId(BIDDING_ID);
        b.setCode("BID-001");
        b.setTitle("Test bidding");
        b.setStartAt(LocalDateTime.now());
        b.setEndAt(LocalDateTime.now().plusDays(7));
        b.setStatus(status);
        b.setTenantId(TENANT);
        return b;
    }

    private ProcBiddingParticipant part(long id, long supplierId, String amount, String status) {
        ProcBiddingParticipant p = new ProcBiddingParticipant();
        p.setId(id);
        p.setBiddingId(BIDDING_ID);
        p.setSupplierId(supplierId);
        if (amount != null) p.setBidAmount(new BigDecimal(amount));
        p.setStatus(status);
        p.setTenantId(TENANT);
        return p;
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

    // 23. award 必须选 status=joined 的 participant
    @Test
    void award_selectingInvitedParticipant_throws409() {
        ProcBidding b = bidding("published");
        when(biddingMapper.selectById(BIDDING_ID)).thenReturn(b);
        ProcBiddingParticipant p = part(50L, SUPPLIER_A, "100", "invited");
        when(participantMapper.selectById(50L)).thenReturn(p);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> biddingService.award(BIDDING_ID, 50L));
        assertEquals(409, ex.getCode());
    }

    // 24. invite 黑名单拒绝
    @Test
    void invite_blacklistSupplier_throws409() {
        ProcBidding b = bidding("published");
        when(biddingMapper.selectById(BIDDING_ID)).thenReturn(b);
        ProcSupplier bad = supplier(SUPPLIER_A, "BadCo", "blacklist", "0");
        when(supplierMapper.selectById(SUPPLIER_A)).thenReturn(bad);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> biddingService.inviteSuppliers(BIDDING_ID, List.of(SUPPLIER_A)));
        assertEquals(409, ex.getCode());
    }

    // 25. evaluate 综合分排序
    @Test
    void evaluate_sortsByCompositeScore() {
        ProcBidding b = bidding("published");
        when(biddingMapper.selectById(BIDDING_ID)).thenReturn(b);
        ProcBiddingParticipant p1 = part(11L, SUPPLIER_A, "100", "joined");
        ProcBiddingParticipant p2 = part(12L, SUPPLIER_B, "110", "joined");
        when(participantMapper.findByBidding(eq(TENANT), eq(BIDDING_ID)))
            .thenReturn(new ArrayList<>(List.of(p1, p2)));
        when(supplierMapper.selectById(SUPPLIER_A)).thenReturn(supplier(SUPPLIER_A, "A", "active", "4.0"));
        when(supplierMapper.selectById(SUPPLIER_B)).thenReturn(supplier(SUPPLIER_B, "B", "active", "3.0"));

        List<BiddingEvaluationDto> result = biddingService.evaluate(BIDDING_ID);
        assertEquals(2, result.size());
        // 第一名应该是最低价
        assertEquals(0, result.get(0).getBidAmount().compareTo(new BigDecimal("100")));
        // 综合分递减
        assertTrue(result.get(0).getCompositeScore() >= result.get(1).getCompositeScore());
    }

    // 26. evaluate 翻转 bidding 状态 → evaluating
    @Test
    void evaluate_flipsBiddingToEvaluating() {
        ProcBidding b = bidding("published");
        when(biddingMapper.selectById(BIDDING_ID)).thenReturn(b);
        ProcBiddingParticipant p1 = part(11L, SUPPLIER_A, "100", "joined");
        when(participantMapper.findByBidding(eq(TENANT), eq(BIDDING_ID)))
            .thenReturn(new ArrayList<>(List.of(p1)));
        when(supplierMapper.selectById(SUPPLIER_A)).thenReturn(supplier(SUPPLIER_A, "A", "active", "4.0"));

        biddingService.evaluate(BIDDING_ID);
        assertEquals("evaluating", b.getStatus());
    }

    // 27. publish 仅 draft 可发
    @Test
    void publish_alreadyPublished_throws409() {
        ProcBidding b = bidding("published");
        when(biddingMapper.selectById(BIDDING_ID)).thenReturn(b);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> biddingService.publish(BIDDING_ID));
        assertEquals(409, ex.getCode());
    }
}