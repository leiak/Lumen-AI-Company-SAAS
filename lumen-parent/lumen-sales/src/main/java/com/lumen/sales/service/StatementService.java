package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Order;
import com.lumen.sales.entity.Receivable;
import com.lumen.sales.entity.Statement;
import com.lumen.sales.mapper.OrderMapper;
import com.lumen.sales.mapper.ReceivableMapper;
import com.lumen.sales.mapper.StatementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账单服务。generate 汇总客户在 [periodStart, periodEnd] 内的订单 + 应收 +
 * 已收金额。重复期间由 UNIQUE(customer_id, period_start, period_end, deleted) 拦截。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_CONFIRMED = "confirmed";

    private final StatementMapper statementMapper;
    private final OrderMapper orderMapper;
    private final ReceivableMapper receivableMapper;
    private final CustomerService customerService;

    public IPage<Statement> page(int pageNum, int pageSize, Long customerId) {
        var w = new LambdaQueryWrapper<Statement>().orderByDesc(Statement::getId);
        if (customerId != null) w.eq(Statement::getCustomerId, customerId);
        return statementMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Statement get(Long id) {
        Statement s = statementMapper.selectById(id);
        if (s == null) throw new ServiceException(404, "Statement not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(s.getTenantId())) {
                throw new ServiceException(404, "Statement not found: " + id);
            }
        }
        return s;
    }

    /**
     * 生成对账单 — 汇总客户在 [periodStart, periodEnd] 内的应收 / 已收金额。
     * 期间唯一性由 UNIQUE(customer_id, period_start, period_end, deleted) 保证。
     */
    @Transactional
    public Statement generate(Long customerId, LocalDate periodStart, LocalDate periodEnd) {
        UserContext ctx = requireContext();
        if (periodStart == null || periodEnd == null) {
            throw new ServiceException(400, "periodStart / periodEnd required");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new ServiceException(400, "periodEnd must be >= periodStart");
        }
        customerService.get(customerId);

        Statement exist = statementMapper.findByCustomerAndPeriod(
            customerId, periodStart, periodEnd, ctx.getTenantId());
        if (exist != null) {
            throw new ServiceException(409, "Statement already exists for this period");
        }

        // 汇总应收金额
        BigDecimal receivableAmount = receivableMapper.sumAmountByCustomer(customerId, ctx.getTenantId());
        BigDecimal collectedAmount = receivableMapper.sumCollectedByCustomer(customerId, ctx.getTenantId());

        // 总金额 = 期间内完成的订单总金额;fallback 到 receivable 应收金额
        BigDecimal total = receivableAmount == null ? BigDecimal.ZERO : receivableAmount;
        try {
            List<Order> orders = orderMapper.findByCustomer(customerId, OrderService.STATUS_COMPLETED, ctx.getTenantId());
            BigDecimal orderTotal = BigDecimal.ZERO;
            for (Order o : orders) {
                if (o.getOrderDate() != null
                    && !o.getOrderDate().isBefore(periodStart)
                    && !o.getOrderDate().isAfter(periodEnd)) {
                    if (o.getTotalAmount() != null) {
                        orderTotal = orderTotal.add(o.getTotalAmount());
                    }
                }
            }
            if (orderTotal.signum() > 0) {
                total = orderTotal;
            }
        } catch (Exception ex) {
            log.warn("Statement order aggregation skipped: {}", ex.getMessage());
        }

        Statement s = new Statement();
        s.setCustomerId(customerId);
        s.setPeriodStart(periodStart);
        s.setPeriodEnd(periodEnd);
        s.setTotalAmount(total);
        s.setStatus(STATUS_DRAFT);
        s.setGeneratedAt(LocalDateTime.now());
        s.setTenantId(ctx.getTenantId());
        statementMapper.insert(s);
        log.info("Statement generated id={} customerId={} total={} collected={}",
            s.getId(), customerId, total, collectedAmount);
        return s;
    }

    /** Send — TODO P5 email. */
    @Transactional
    public Statement send(Long id) {
        Statement s = get(id);
        if (!STATUS_DRAFT.equals(s.getStatus())) {
            throw new ServiceException(409, "Only draft statement can be sent: " + s.getStatus());
        }
        s.setStatus(STATUS_SENT);
        s.setSentAt(LocalDateTime.now());
        statementMapper.updateById(s);
        // TODO P5: 调用 message-center 邮件发送
        return s;
    }

    @Transactional
    public Statement confirm(Long id) {
        Statement s = get(id);
        if (!STATUS_SENT.equals(s.getStatus())) {
            throw new ServiceException(409, "Only sent statement can be confirmed: " + s.getStatus());
        }
        s.setStatus(STATUS_CONFIRMED);
        statementMapper.updateById(s);
        return s;
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }
}