package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.entity.ProcOrder;
import com.lumen.procurement.entity.ProcReceipt;
import com.lumen.procurement.mapper.ProcOrderMapper;
import com.lumen.procurement.mapper.ProcPaymentMapper;
import com.lumen.procurement.mapper.ProcReceiptMapper;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

/**
 * 采购 dashboard 聚合统计。
 *
 * <p>输出: 今日订单数, 本周订单金额, 未付款金额, 活跃供应商数, 待处理收货单数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcProcurementDashboardService {

    private final ProcOrderMapper orderMapper;
    private final ProcPaymentMapper paymentMapper;
    private final ProcReceiptMapper receiptMapper;
    private final ProcSupplierMapper supplierMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public Map<String, Object> stats() {
        UserContext ctx = requireCtx();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        long todayOrders = orderMapper.countToday(ctx.getTenantId(), today);
        BigDecimal weekAmount = orderMapper.sumAmountBetween(ctx.getTenantId(), weekStart, today);
        BigDecimal unpaid = paymentMapper.sumUnpaid(ctx.getTenantId());
        long activeSuppliers = supplierMapper.countActive(ctx.getTenantId());

        long pendingReceipts = receiptMapper.selectCount(new LambdaQueryWrapper<ProcReceipt>()
            .eq(ProcReceipt::getTenantId, ctx.getTenantId())
            .eq(ProcReceipt::getStatus, ProcReceiptService.STATUS_PENDING)).longValue();

        Map<String, Object> out = new HashMap<>();
        out.put("todayOrders", todayOrders);
        out.put("weekStart", weekStart.toString());
        out.put("weekAmount", weekAmount);
        out.put("unpaidAmount", unpaid);
        out.put("activeSuppliers", activeSuppliers);
        out.put("pendingReceipts", pendingReceipts);
        return out;
    }
}