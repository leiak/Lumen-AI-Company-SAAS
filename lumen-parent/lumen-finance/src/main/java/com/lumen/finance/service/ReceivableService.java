package com.lumen.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.entity.FinReceivable;
import com.lumen.finance.mapper.FinReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 应收单 service。collect 累积 collected_amount + 自动更新状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivableService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_COLLECTED = "collected";

    private final FinReceivableMapper receivableMapper;

    public List<FinReceivable> listByCustomerAndStatus(Long customerId, String status) {
        requireTenant();
        return receivableMapper.findByCustomerAndStatus(customerId, status);
    }

    public FinReceivable get(Long id) {
        UserContext ctx = requireUserContext();
        FinReceivable r = receivableMapper.selectById(id);
        if (r == null) throw new ServiceException(404, "Receivable not found: " + id);
        assertTenant(r, ctx);
        return r;
    }

    @Transactional
    public FinReceivable save(FinReceivable req) {
        UserContext ctx = requireUserContext();
        if (req.getCollectedAmount() == null) req.setCollectedAmount(BigDecimal.ZERO);
        if (req.getStatus() == null) req.setStatus(STATUS_PENDING);
        req.setTenantId(ctx.getTenantId());
        receivableMapper.insert(req);
        return req;
    }

    /**
     * 累积收款。amount 必须 > 0，累加后 collectedAmount 不能超过 amount。
     */
    @Transactional
    public FinReceivable collect(Long id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ServiceException(400, "amount must be positive");
        }
        FinReceivable r = get(id);
        if (STATUS_COLLECTED.equals(r.getStatus())) {
            throw new ServiceException(409, "Receivable already fully collected");
        }
        BigDecimal newCollected = r.getCollectedAmount().add(amount);
        if (newCollected.compareTo(r.getAmount()) > 0) {
            throw new ServiceException(400,
                "Collected amount would exceed receivable amount: "
                    + newCollected + " > " + r.getAmount());
        }
        r.setCollectedAmount(newCollected);
        if (newCollected.compareTo(r.getAmount()) == 0) {
            r.setStatus(STATUS_COLLECTED);
        } else {
            r.setStatus(STATUS_PARTIAL);
        }
        receivableMapper.updateById(r);
        log.info("Receivable collected id={} +amount={} status={}", r.getId(), amount, r.getStatus());
        return r;
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

    private void assertTenant(FinReceivable r, UserContext ctx) {
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(r.getTenantId())) {
            throw new ServiceException(404, "Receivable not found: " + r.getId());
        }
    }
}
