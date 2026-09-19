package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.entity.InvSafetyStock;
import com.lumen.inventory.mapper.InvSafetyStockMapper;
import com.lumen.inventory.mapper.InvStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全库存阈值检查。
 *
 * <p>alert_status (安全要求 #10):</p>
 * <ul>
 *   <li>current = 0 → out_of_stock</li>
 *   <li>0 < current < min → low</li>
 *   <li>min <= current <= max → normal</li>
 *   <li>current > max → overstock</li>
 * </ul>
 * <p>check() 后写 last_alert_at (若非 normal)。TODO P5: 调 message-center 触发告警。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyStockService {

    public static final String ALERT_NORMAL = "normal";
    public static final String ALERT_LOW = "low";
    public static final String ALERT_OUT_OF_STOCK = "out_of_stock";
    public static final String ALERT_OVERSTOCK = "overstock";

    private final InvSafetyStockMapper safetyStockMapper;
    private final InvStockMapper stockMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    /**
     * 检查 (warehouseId, itemId) 的安全库存。
     * 更新 current_quantity + alert_status + last_alert_at。
     */
    @Transactional
    public InvSafetyStock check(Long warehouseId, Long itemId) {
        UserContext ctx = requireCtx();
        InvSafetyStock rule = safetyStockMapper.selectList(new LambdaQueryWrapper<InvSafetyStock>()
                .eq(InvSafetyStock::getWarehouseId, warehouseId)
                .eq(InvSafetyStock::getItemId, itemId))
            .stream().findFirst().orElse(null);
        if (rule == null) {
            // 没有规则则不处理 (业务可后续 P5 自动创建默认规则)
            return null;
        }
        // 计算 current_quantity = sum(qty) over this warehouse + item
        BigDecimal current = sumByWarehouseAndItem(ctx.getTenantId(), warehouseId, itemId);
        rule.setCurrentQuantity(current);
        String newStatus = computeAlertStatus(current, rule.getMinQuantity(), rule.getMaxQuantity());
        rule.setAlertStatus(newStatus);
        if (!ALERT_NORMAL.equals(newStatus)) {
            rule.setLastAlertAt(LocalDateTime.now());
            // TODO P5: 调 message-center 触发告警
        }
        safetyStockMapper.updateById(rule);
        log.info("SafetyStock checked wh={} item={} current={} status={}",
            warehouseId, itemId, current, newStatus);
        return rule;
    }

    public List<InvSafetyStock> findByWarehouse(Long warehouseId) {
        requireCtx();
        return safetyStockMapper.findByWarehouse(UserContextHolder.get().getTenantId(), warehouseId);
    }

    public List<InvSafetyStock> findLowStock() {
        requireCtx();
        return safetyStockMapper.findLowStock(UserContextHolder.get().getTenantId());
    }

    public List<InvSafetyStock> findOverstock() {
        requireCtx();
        return safetyStockMapper.findByAlertStatus(UserContextHolder.get().getTenantId(), ALERT_OVERSTOCK);
    }

    private String computeAlertStatus(BigDecimal current, BigDecimal min, BigDecimal max) {
        if (current == null || current.compareTo(BigDecimal.ZERO) == 0) return ALERT_OUT_OF_STOCK;
        if (min != null && current.compareTo(min) < 0) return ALERT_LOW;
        if (max != null && current.compareTo(max) > 0) return ALERT_OVERSTOCK;
        return ALERT_NORMAL;
    }

    /**
     * 仓库 + SKU 的 sum (sumByItem/sumByWarehouse 都偏粗)。用 selectList 自过滤。
     */
    private BigDecimal sumByWarehouseAndItem(Long tenantId, Long warehouseId, Long itemId) {
        List<com.lumen.inventory.entity.InvStock> rows = stockMapper.selectList(
            new LambdaQueryWrapper<com.lumen.inventory.entity.InvStock>()
                .eq(com.lumen.inventory.entity.InvStock::getTenantId, tenantId)
                .eq(com.lumen.inventory.entity.InvStock::getWarehouseId, warehouseId)
                .eq(com.lumen.inventory.entity.InvStock::getItemId, itemId));
        BigDecimal sum = BigDecimal.ZERO;
        for (com.lumen.inventory.entity.InvStock s : rows) {
            if (s.getQuantity() != null) sum = sum.add(s.getQuantity());
        }
        return sum;
    }
}