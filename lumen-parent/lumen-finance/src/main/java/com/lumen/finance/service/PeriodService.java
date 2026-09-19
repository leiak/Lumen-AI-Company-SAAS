package com.lumen.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinPeriod;
import com.lumen.finance.entity.FinVoucher;
import com.lumen.finance.mapper.FinPayableMapper;
import com.lumen.finance.mapper.FinPeriodMapper;
import com.lumen.finance.mapper.FinReceivableMapper;
import com.lumen.finance.mapper.FinVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会计期间 service。
 *
 * <p>close 必须满足:
 *   1) 期内所有 voucher 状态 ∈ {posted, reversed} (即非 draft);
 *   2) 期内所有 receivable 状态 = collected;
 *   3) 期内所有 payable 状态 = paid。
 * lock 后任何人不能再 close/reopen。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodService {

    public static final String STATUS_OPEN = "open";
    public static final String STATUS_CLOSED = "closed";
    public static final String STATUS_LOCKED = "locked";

    private final FinPeriodMapper periodMapper;
    private final FinVoucherMapper voucherMapper;
    private final FinReceivableMapper receivableMapper;
    private final FinPayableMapper payableMapper;

    public List<FinPeriod> list() {
        requireTenant();
        return periodMapper.selectList(new LambdaQueryWrapper<FinPeriod>()
            .orderByDesc(FinPeriod::getYear)
            .orderByDesc(FinPeriod::getMonth));
    }

    public FinPeriod get(Long id) {
        UserContext ctx = requireUserContext();
        FinPeriod p = periodMapper.selectById(id);
        if (p == null) throw new ServiceException(404, "Period not found: " + id);
        assertTenant(p, ctx);
        return p;
    }

    @Transactional
    public FinPeriod save(FinPeriod req) {
        UserContext ctx = requireUserContext();
        if (req.getYear() == null || req.getMonth() == null) {
            throw new ServiceException(400, "year/month is required");
        }
        if (req.getStatus() == null) req.setStatus(STATUS_OPEN);
        FinPeriod existing = periodMapper.findByYearMonth(req.getYear(), req.getMonth());
        if (existing != null) {
            throw new ServiceException(409, "Period already exists: " + req.getYear() + "-" + req.getMonth());
        }
        req.setTenantId(ctx.getTenantId());
        periodMapper.insert(req);
        return req;
    }

    /**
     * 关闭期间。安全要求 #10: 校验所有 voucher 已 posted + 应收/应付 closed。
     */
    @Transactional
    public FinPeriod close(String period) {
        UserContext ctx = requireUserContext();
        FinPeriod p = requirePeriod(period, ctx);
        if (STATUS_LOCKED.equals(p.getStatus())) {
            // 安全要求 #12
            throw new ServiceException(403, "Period is locked; cannot close or reopen");
        }
        if (STATUS_CLOSED.equals(p.getStatus())) {
            throw new ServiceException(409, "Period already closed: " + period);
        }

        // 安全要求 #10: 校验链
        List<FinVoucher> vouchers = voucherMapper.findByPeriod(period);
        for (FinVoucher v : vouchers) {
            if ("draft".equals(v.getStatus())) {
                throw new ServiceException(409,
                    "Cannot close period " + period + ": voucher id=" + v.getId()
                        + " still in draft");
            }
        }
        List<com.lumen.finance.entity.FinReceivable> openRecv = receivableMapper.listOpenForCloseCheck();
        for (com.lumen.finance.entity.FinReceivable r : openRecv) {
            // Within current tenant only — TenantLineInnerInterceptor already scopes this.
            if (ctx.getTenantId() != null && ctx.getTenantId().equals(r.getTenantId())) {
                throw new ServiceException(409,
                    "Cannot close period " + period + ": receivable id=" + r.getId()
                        + " not fully collected");
            }
        }
        List<com.lumen.finance.entity.FinPayable> openPay = payableMapper.listOpenForCloseCheck();
        for (com.lumen.finance.entity.FinPayable pay : openPay) {
            if (ctx.getTenantId() != null && ctx.getTenantId().equals(pay.getTenantId())) {
                throw new ServiceException(409,
                    "Cannot close period " + period + ": payable id=" + pay.getId()
                        + " not fully paid");
            }
        }

        p.setStatus(STATUS_CLOSED);
        p.setClosedAt(LocalDateTime.now());
        periodMapper.updateById(p);
        log.info("Period closed: {} by {}", period, ctx.getUserId());
        return p;
    }

    /**
     * 锁定期间。锁定后任何人都不能再 open/close。
     */
    @Transactional
    public FinPeriod lock(String period) {
        UserContext ctx = requireUserContext();
        FinPeriod p = requirePeriod(period, ctx);
        if (STATUS_LOCKED.equals(p.getStatus())) {
            throw new ServiceException(409, "Period already locked");
        }
        if (STATUS_OPEN.equals(p.getStatus())) {
            throw new ServiceException(409, "Close the period before locking: " + period);
        }
        p.setStatus(STATUS_LOCKED);
        periodMapper.updateById(p);
        log.info("Period locked: {} by {}", period, ctx.getUserId());
        return p;
    }

    /**
     * 计算下一期间 (yyyy-MM)。用于 UI 提示和反向期初。
     */
    public String nextPeriod(String currentPeriod) {
        if (currentPeriod == null || !currentPeriod.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException(400, "currentPeriod must be yyyy-MM");
        }
        int year = Integer.parseInt(currentPeriod.substring(0, 4));
        int month = Integer.parseInt(currentPeriod.substring(5, 7));
        if (month == 12) return (year + 1) + "-01";
        return String.format("%04d-%02d", year, month + 1);
    }

    // ---------- helpers ----------
    private FinPeriod requirePeriod(String period, UserContext ctx) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException(400, "period must be yyyy-MM");
        }
        FinPeriod p = periodMapper.findByYearMonth(
            Integer.parseInt(period.substring(0, 4)),
            Integer.parseInt(period.substring(5, 7)));
        if (p == null) {
            throw new ServiceException(404, "Period not found: " + period);
        }
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(p.getTenantId())) {
            throw new ServiceException(404, "Period not found: " + period);
        }
        return p;
    }

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

    private void assertTenant(FinPeriod p, UserContext ctx) {
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(p.getTenantId())) {
            throw new ServiceException(404, "Period not found: " + p.getId());
        }
    }
}
