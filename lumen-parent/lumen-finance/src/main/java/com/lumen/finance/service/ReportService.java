package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报表 service。三大报表的完整计算涉及科目余额结转/利润表项目分类/
 * 现金流量表分项调整, B1 阶段先返回骨架数据结构 + TODO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    /**
     * 资产负债表（period = 期末 yyyy-MM）。
     *
     * <p>TODO: 完整逻辑 — 按 subject.type 分组 (asset/liability/equity),
     * 汇总期末余额 (sum(debit) - sum(credit) 按 balance_direction 调整),
     * 验证 asset = liability + equity。</p>
     */
    public Map<String, Object> balanceSheet(String period) {
        requireTenant();
        validatePeriod(period);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("generatedAt", System.currentTimeMillis());
        out.put("assets", BigDecimal.ZERO);
        out.put("liabilities", BigDecimal.ZERO);
        out.put("equity", BigDecimal.ZERO);
        out.put("balanced", false);
        out.put("todo", "TODO: aggregate subject balances per type, verify asset == liability + equity");
        log.debug("balanceSheet({}) — TODO", period);
        return out;
    }

    /**
     * 利润表 (periodFrom..periodTo)。
     *
     * <p>TODO: 完整逻辑 — 按 subject.type = income/expense 汇总, 计算毛利/净利,
     * 期段汇总。</p>
     */
    public Map<String, Object> incomeStatement(String periodFrom, String periodTo) {
        requireTenant();
        validatePeriod(periodFrom);
        validatePeriod(periodTo);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodFrom", periodFrom);
        out.put("periodTo", periodTo);
        out.put("totalIncome", BigDecimal.ZERO);
        out.put("totalExpense", BigDecimal.ZERO);
        out.put("netProfit", BigDecimal.ZERO);
        out.put("todo", "TODO: aggregate income/expense subject balances across period range");
        log.debug("incomeStatement({}, {}) — TODO", periodFrom, periodTo);
        return out;
    }

    /**
     * 现金流量表 (periodFrom..periodTo)。
     *
     * <p>TODO: 完整逻辑 — 按业务活动/投资活动/筹资活动分类, 现金科目 (subject.code 1xxx 现金/银行存款) 期初/期末差额。</p>
     */
    public Map<String, Object> cashFlow(String periodFrom, String periodTo) {
        requireTenant();
        validatePeriod(periodFrom);
        validatePeriod(periodTo);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodFrom", periodFrom);
        out.put("periodTo", periodTo);
        out.put("operating", BigDecimal.ZERO);
        out.put("investing", BigDecimal.ZERO);
        out.put("financing", BigDecimal.ZERO);
        out.put("netChange", BigDecimal.ZERO);
        out.put("todo", "TODO: classify cash subject movements into 3 activities");
        log.debug("cashFlow({}, {}) — TODO", periodFrom, periodTo);
        return out;
    }

    private void validatePeriod(String period) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException(400, "period must be yyyy-MM");
        }
    }

    private UserContext requireTenant() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return ctx;
    }
}
