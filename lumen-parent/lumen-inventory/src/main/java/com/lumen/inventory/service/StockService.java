package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.StockDeductionDto;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.mapper.InvStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存主操作: addStock / lock / unlock / consume / consumeFifo。
 *
 * <p>安全要点:</p>
 * <ul>
 *   <li>addStock 校验 quantity >= 0 (安全要求 #4)。</li>
 *   <li>consume 校验 available_quantity >= quantity,失败 409 (安全要求 #4)。</li>
 *   <li>lock/unlock 必须配对 (安全要求 #11)。</li>
 *   <li>FIFO 按 last_in_at ASC (安全要求 #8)。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final InvStockMapper stockMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    /**
     * 入库加可用库存。UNIQUE 冲突时走 update (更新 quantity)。
     * quantity 必须 >= 0。
     */
    @Transactional
    public InvStock addStock(Long warehouseId, Long locationId, Long itemId,
                             String batchNo, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(400, "quantity must be >= 0");
        }
        UserContext ctx = requireCtx();
        InvStock existing = stockMapper.findByBatch(ctx.getTenantId(), warehouseId, locationId, itemId, batchNo);
        if (existing == null) {
            InvStock s = new InvStock();
            s.setTenantId(ctx.getTenantId());
            s.setWarehouseId(warehouseId);
            s.setLocationId(locationId);
            s.setItemId(itemId);
            s.setBatchNo(batchNo);
            s.setQuantity(quantity);
            s.setAvailableQuantity(quantity);
            s.setLockedQuantity(BigDecimal.ZERO);
            s.setLastInAt(LocalDateTime.now());
            stockMapper.insert(s);
            log.info("Stock created id={} wh={} loc={} item={} batch={} qty={}",
                s.getId(), warehouseId, locationId, itemId, batchNo, quantity);
            return s;
        }
        if (!existing.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stock not found");
        }
        BigDecimal newQty = existing.getQuantity().add(quantity);
        BigDecimal newAvail = existing.getAvailableQuantity().add(quantity);
        existing.setQuantity(newQty);
        existing.setAvailableQuantity(newAvail);
        existing.setLastInAt(LocalDateTime.now());
        stockMapper.updateById(existing);
        log.info("Stock increased id={} qty={} avail={}", existing.getId(), newQty, newAvail);
        return existing;
    }

    /**
     * 锁定库存 (出库预留)。从 available_quantity 转到 locked_quantity。
     */
    @Transactional
    public InvStock lock(Long id, BigDecimal reservedQty) {
        if (reservedQty == null || reservedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(400, "reservedQty must be > 0");
        }
        UserContext ctx = requireCtx();
        InvStock s = stockMapper.selectById(id);
        if (s == null || !s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stock not found: " + id);
        }
        if (s.getAvailableQuantity().compareTo(reservedQty) < 0) {
            throw new ServiceException(409,
                "Insufficient available qty: stock=" + s.getAvailableQuantity() + " request=" + reservedQty);
        }
        s.setAvailableQuantity(s.getAvailableQuantity().subtract(reservedQty));
        s.setLockedQuantity(s.getLockedQuantity().add(reservedQty));
        stockMapper.updateById(s);
        log.info("Stock locked id={} reserved={}", id, reservedQty);
        return s;
    }

    /**
     * 解锁库存 (出库取消预留)。从 locked_quantity 退回 available_quantity。
     */
    @Transactional
    public InvStock unlock(Long id, BigDecimal reservedQty) {
        if (reservedQty == null || reservedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(400, "reservedQty must be > 0");
        }
        UserContext ctx = requireCtx();
        InvStock s = stockMapper.selectById(id);
        if (s == null || !s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stock not found: " + id);
        }
        if (s.getLockedQuantity().compareTo(reservedQty) < 0) {
            throw new ServiceException(409, "Insufficient locked qty to unlock");
        }
        s.setLockedQuantity(s.getLockedQuantity().subtract(reservedQty));
        s.setAvailableQuantity(s.getAvailableQuantity().add(reservedQty));
        stockMapper.updateById(s);
        log.info("Stock unlocked id={} released={}", id, reservedQty);
        return s;
    }

    /**
     * 扣减库存 (出库)。直接消耗 locked_quantity + quantity。
     * 用于 confirm(inout type=out) 时 (通常 out 流程是 lock → consume)。
     * 也支持直接 consume (locked_quantity 不变,quantity 减少)。
     */
    @Transactional
    public InvStock consume(Long id, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(400, "quantity must be > 0");
        }
        UserContext ctx = requireCtx();
        InvStock s = stockMapper.selectById(id);
        if (s == null || !s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stock not found: " + id);
        }
        // 安全要求 #4: 不能扣成负数
        if (s.getAvailableQuantity().compareTo(quantity) < 0) {
            throw new ServiceException(409,
                "Insufficient available qty: stock=" + s.getAvailableQuantity() + " request=" + quantity);
        }
        s.setQuantity(s.getQuantity().subtract(quantity));
        s.setAvailableQuantity(s.getAvailableQuantity().subtract(quantity));
        s.setLastOutAt(LocalDateTime.now());
        stockMapper.updateById(s);
        log.info("Stock consumed id={} qty={}", id, quantity);
        return s;
    }

    /**
     * 按 FIFO 顺序扣减可用库存 (last_in_at ASC)。
     * 用于 confirm(type=out, 无指定 stock) 时,或调拨 ship 时的源仓库扣减。
     *
     * @return 扣减明细,各 stock 实际扣减量。
     */
    @Transactional
    public List<StockDeductionDto> consumeFifo(Long itemId, Long warehouseId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(400, "quantity must be > 0");
        }
        UserContext ctx = requireCtx();
        List<InvStock> stocks = stockMapper.findByWarehouseAndItem(ctx.getTenantId(), warehouseId, itemId);
        BigDecimal remaining = quantity;
        List<StockDeductionDto> deductions = new ArrayList<>();
        for (InvStock s : stocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal avail = s.getAvailableQuantity() == null ? BigDecimal.ZERO : s.getAvailableQuantity();
            if (avail.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal take = avail.compareTo(remaining) >= 0 ? remaining : avail;
            s.setQuantity(s.getQuantity().subtract(take));
            s.setAvailableQuantity(avail.subtract(take));
            s.setLastOutAt(LocalDateTime.now());
            stockMapper.updateById(s);
            remaining = remaining.subtract(take);
            deductions.add(new StockDeductionDto(s.getId(), s.getItemId(), s.getWarehouseId(),
                s.getBatchNo(), take));
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException(409,
                "Insufficient total FIFO stock: short=" + remaining + " item=" + itemId);
        }
        log.info("FIFO consume item={} wh={} total={} deductions={}",
            itemId, warehouseId, quantity, deductions.size());
        return deductions;
    }

    /**
     * 列出某仓库某 SKU 的全部批次 (FIFO 顺序)。
     */
    public List<InvStock> findAvailable(Long itemId, Long warehouseId) {
        requireCtx();
        return stockMapper.findByWarehouseAndItem(warehouseId, warehouseId, itemId);
    }

    public List<InvStock> findByWarehouse(Long warehouseId) {
        requireCtx();
        return stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
            .eq(InvStock::getWarehouseId, warehouseId));
    }

    public List<InvStock> findByItem(Long itemId) {
        requireCtx();
        return stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
            .eq(InvStock::getItemId, itemId));
    }

    public InvStock getById(Long id) {
        UserContext ctx = requireCtx();
        InvStock s = stockMapper.selectById(id);
        if (s == null || !s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stock not found: " + id);
        }
        return s;
    }
}