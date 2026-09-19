package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.mapper.OpportunityMapper;
import com.lumen.sales.mapper.ReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售看板。
 * <ul>
 *   <li>funnel — 按阶段统计 count / total_amount</li>
 *   <li>this-month — 本月 won 销售金额</li>
 *   <li>collectionRate — 已收 / 应收</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesDashboardService {

    private final OpportunityMapper opportunityMapper;
    private final ReceivableMapper receivableMapper;
    private final CustomerService customerService;

    public Map<String, Object> funnel() {
        UserContext ctx = requireContext();
        List<Map<String, Object>> rows = opportunityMapper.funnelByStage(ctx.getTenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        // Ensure stable stage order
        String[] order = {"qualification", "proposal", "negotiation", "won", "lost"};
        Map<String, Map<String, Object>> byStage = new LinkedHashMap<>();
        for (String s : order) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("stage", s);
            entry.put("count", 0);
            entry.put("totalAmount", BigDecimal.ZERO);
            byStage.put(s, entry);
        }
        for (Map<String, Object> row : rows) {
            String stage = String.valueOf(row.get("stage"));
            Map<String, Object> entry = byStage.computeIfAbsent(stage, k -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("stage", k);
                e.put("count", 0);
                e.put("totalAmount", BigDecimal.ZERO);
                return e;
            });
            Object cnt = row.get("cnt");
            Object total = row.get("total_amount");
            if (cnt instanceof Number) entry.put("count", ((Number) cnt).intValue());
            if (total instanceof BigDecimal) entry.put("totalAmount", total);
            else if (total instanceof Number) entry.put("totalAmount", new BigDecimal(total.toString()));
        }
        result.put("stages", byStage.values());
        return result;
    }

    public Map<String, Object> thisMonth() {
        UserContext ctx = requireContext();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal won = opportunityMapper.thisMonthWonAmount(ctx.getTenantId(), startOfMonth);
        Map<String, Object> r = new HashMap<>();
        r.put("month", LocalDate.now().withDayOfMonth(1));
        r.put("wonAmount", won == null ? BigDecimal.ZERO : won);
        return r;
    }

    public Map<String, Object> collectionRate(Long customerId) {
        UserContext ctx = requireContext();
        BigDecimal amount = receivableMapper.sumAmountByCustomer(customerId, ctx.getTenantId());
        BigDecimal collected = receivableMapper.sumCollectedByCustomer(customerId, ctx.getTenantId());
        BigDecimal a = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal c = collected == null ? BigDecimal.ZERO : collected;
        BigDecimal rate = a.signum() == 0 ? BigDecimal.ZERO
            : c.multiply(BigDecimal.valueOf(100)).divide(a, 2, RoundingMode.HALF_UP);
        Map<String, Object> r = new HashMap<>();
        r.put("customerId", customerId);
        r.put("totalAmount", a);
        r.put("collectedAmount", c);
        r.put("collectionRate", rate);
        return r;
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }
}