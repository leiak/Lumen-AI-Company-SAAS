package com.lumen.inventory.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.StockDeductionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * FIFO 扣减门面 (安全要求 #8):按 last_in_at ASC 从最早入库的批次开始扣减。
 * 实际算法在 {@link StockService#consumeFifo(Long, Long, BigDecimal)} 内实现。
 *
 * <p>FifoService 仅作为业务层 facade,后续 P5 可在此加入缓存预热 / 批量扣减优化。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FifoService {

    private final StockService stockService;

    /**
     * 按 FIFO 扣减。校验 user/tenant context。
     */
    public List<StockDeductionDto> consumeFifo(Long itemId, Long warehouseId, BigDecimal quantity) {
        if (itemId == null) throw new ServiceException(400, "itemId is required");
        if (warehouseId == null) throw new ServiceException(400, "warehouseId is required");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(400, "quantity must be > 0");
        }
        if (UserContextHolder.get() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        log.info("FIFO consume item={} warehouse={} qty={}", itemId, warehouseId, quantity);
        return stockService.consumeFifo(itemId, warehouseId, quantity);
    }
}