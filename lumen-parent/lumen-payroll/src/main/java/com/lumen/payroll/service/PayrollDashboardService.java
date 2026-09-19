package com.lumen.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.mapper.PaySlipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 薪资仪表盘服务。
 *  - 本月算薪进度 (draft/calculated/confirmed/paid 计数)
 *  - 已发放总额 (paid)
 *  - 未发放人数 (draft + calculated + confirmed)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollDashboardService {

    private final PaySlipMapper slipMapper;

    public Map<String, Object> stats() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        String currentPeriod = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<PaySlip> all = slipMapper.selectList(new LambdaQueryWrapper<PaySlip>()
            .eq(PaySlip::getTenantId, ctx.getTenantId())
            .eq(PaySlip::getPeriod, currentPeriod)
            .eq(PaySlip::getDeleted, 0));

        Map<String, Object> out = new HashMap<>();
        out.put("period", currentPeriod);

        Map<String, Integer> byStatus = new HashMap<>();
        byStatus.put("draft", 0);
        byStatus.put("calculated", 0);
        byStatus.put("confirmed", 0);
        byStatus.put("paid", 0);

        BigDecimal paidTotal = BigDecimal.ZERO;
        BigDecimal confirmedTotal = BigDecimal.ZERO;
        int unpaidCount = 0;
        for (PaySlip s : all) {
            String st = s.getStatus() == null ? "draft" : s.getStatus();
            byStatus.merge(st, 1, Integer::sum);
            if ("paid".equals(st)) {
                if (s.getNetSalary() != null) paidTotal = paidTotal.add(s.getNetSalary());
            } else {
                unpaidCount++;
                if ("confirmed".equals(st) && s.getNetSalary() != null) {
                    confirmedTotal = confirmedTotal.add(s.getNetSalary());
                }
            }
        }
        out.put("byStatus", byStatus);
        out.put("paidTotal", paidTotal);
        out.put("confirmedTotal", confirmedTotal);
        out.put("unpaidCount", unpaidCount);
        out.put("totalCount", all.size());
        return out;
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}