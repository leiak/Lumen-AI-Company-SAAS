package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.entity.InvInout;
import com.lumen.inventory.entity.InvSafetyStock;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.mapper.InvInoutMapper;
import com.lumen.inventory.mapper.InvSafetyStockMapper;
import com.lumen.inventory.mapper.InvStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存 dashboard 聚合:
 * <ul>
 *   <li>totalStockValue: sum(quantity) 仓储</li>
 *   <li>lowStockCount: 低于 min 的 item 数</li>
 *   <li>todayInCount: 今日入库单数 (type=in, status=confirmed, inout_date=today)</li>
 *   <li>todayOutCount: 今日出库单数 (type=out, status=confirmed, inout_date=today)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDashboardService {

    private final InvStockMapper stockMapper;
    private final InvInoutMapper inoutMapper;
    private final InvSafetyStockMapper safetyStockMapper;

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
        // 库存总值: sum(quantity) over tenant
        List<InvStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
            .eq(InvStock::getTenantId, ctx.getTenantId()));
        BigDecimal totalValue = BigDecimal.ZERO;
        for (InvStock s : stocks) {
            if (s.getQuantity() != null) totalValue = totalValue.add(s.getQuantity());
        }
        // 低库存数
        List<InvSafetyStock> lows = safetyStockMapper.findLowStock(ctx.getTenantId());
        // 今日入库/出库
        long todayIn = inoutMapper.selectCount(new LambdaQueryWrapper<InvInout>()
            .eq(InvInout::getTenantId, ctx.getTenantId())
            .eq(InvInout::getType, InoutService.TYPE_IN)
            .eq(InvInout::getStatus, InoutService.STATUS_CONFIRMED)
            .eq(InvInout::getInoutDate, today)).longValue();
        long todayOut = inoutMapper.selectCount(new LambdaQueryWrapper<InvInout>()
            .eq(InvInout::getTenantId, ctx.getTenantId())
            .eq(InvInout::getType, InoutService.TYPE_OUT)
            .eq(InvInout::getStatus, InoutService.STATUS_CONFIRMED)
            .eq(InvInout::getInoutDate, today)).longValue();

        Map<String, Object> out = new HashMap<>();
        out.put("totalStockValue", totalValue);
        out.put("lowStockCount", lows.size());
        out.put("todayInCount", todayIn);
        out.put("todayOutCount", todayOut);
        out.put("today", today.toString());
        return out;
    }
}