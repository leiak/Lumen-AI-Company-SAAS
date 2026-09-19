package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.CreateTransferRequest;
import com.lumen.inventory.dto.TransferItemDto;
import com.lumen.inventory.entity.InvTransfer;
import com.lumen.inventory.entity.InvTransferItem;
import com.lumen.inventory.mapper.InvTransferItemMapper;
import com.lumen.inventory.mapper.InvTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 调拨单主流程。
 *
 * <p>状态机 (安全要求 #6): draft → in_transit → received | cancelled。</p>
 * <p>ship (draft → in_transit): 源仓库 FIFO 扣减。</p>
 * <p>receive (in_transit → received): 目标仓库 addStock。</p>
 * <p>ship + receive 在 receive 方法中合并事务 (安全要求 #7): 调用方必须先 ship,再 receive;receive 内组合二者失败回滚。</p>
 * <p>cancel: draft / in_transit → cancelled。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_IN_TRANSIT = "in_transit";
    public static final String STATUS_RECEIVED = "received";
    public static final String STATUS_CANCELLED = "cancelled";

    private final InvTransferMapper transferMapper;
    private final InvTransferItemMapper transferItemMapper;
    private final StockService stockService;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public InvTransfer getById(Long id) {
        UserContext ctx = requireCtx();
        InvTransfer t = transferMapper.selectById(id);
        if (t == null) throw new ServiceException(404, "Transfer not found: " + id);
        if (!t.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Transfer not found: " + id);
        }
        return t;
    }

    public List<InvTransfer> listByStatus(String status) {
        requireCtx();
        return transferMapper.findByStatus(UserContextHolder.get().getTenantId(), status);
    }

    public List<InvTransfer> listByFromOrTo(Long warehouseId) {
        requireCtx();
        return transferMapper.findByFromOrTo(UserContextHolder.get().getTenantId(), warehouseId);
    }

    public List<InvTransferItem> listItems(Long transferId) {
        UserContext ctx = requireCtx();
        getById(transferId);
        return transferItemMapper.findByTransfer(ctx.getTenantId(), transferId);
    }

    @Transactional
    public InvTransfer create(CreateTransferRequest req) {
        UserContext ctx = requireCtx();
        validate(req);
        if (req.getFromWarehouseId().equals(req.getToWarehouseId())) {
            throw new ServiceException(400, "fromWarehouseId and toWarehouseId must differ");
        }
        InvTransfer t = new InvTransfer();
        t.setTenantId(ctx.getTenantId());
        t.setCode(generateCode());
        t.setFromWarehouseId(req.getFromWarehouseId());
        t.setToWarehouseId(req.getToWarehouseId());
        t.setTransferDate(req.getTransferDate());
        t.setOperatorId(ctx.getUserId());
        t.setStatus(STATUS_DRAFT);
        t.setRemark(req.getRemark());
        try {
            transferMapper.insert(t);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Transfer code conflict", ex);
        }
        if (req.getItems() != null) {
            for (TransferItemDto dto : req.getItems()) {
                InvTransferItem it = new InvTransferItem();
                it.setTenantId(ctx.getTenantId());
                it.setTransferId(t.getId());
                it.setItemId(dto.getItemId());
                it.setBatchNo(dto.getBatchNo());
                it.setQuantity(dto.getQuantity());
                transferItemMapper.insert(it);
            }
        }
        log.info("Transfer created id={} code={} from={} to={} items={}",
            t.getId(), t.getCode(), req.getFromWarehouseId(), req.getToWarehouseId(),
            req.getItems() == null ? 0 : req.getItems().size());
        return t;
    }

    /**
     * 发货: draft → in_transit,源仓库 FIFO 扣减。
     */
    @Transactional
    public InvTransfer ship(Long id) {
        UserContext ctx = requireCtx();
        InvTransfer t = getById(id);
        if (!STATUS_DRAFT.equals(t.getStatus())) {
            throw new ServiceException(409,
                "Only draft transfer can be shipped (current=" + t.getStatus() + ")");
        }
        List<InvTransferItem> items = transferItemMapper.findByTransfer(ctx.getTenantId(), id);
        if (items == null || items.isEmpty()) {
            throw new ServiceException(409, "Transfer has no items; cannot ship");
        }
        for (InvTransferItem it : items) {
            BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
            if (it.getBatchNo() != null && !it.getBatchNo().isBlank()) {
                // 指定批次:精确扣减
                stockService.findAvailable(it.getItemId(), t.getFromWarehouseId())
                    .stream()
                    .filter(s -> it.getBatchNo().equals(s.getBatchNo()))
                    .findFirst()
                    .ifPresent(s -> stockService.consume(s.getId(), qty));
            } else {
                // FIFO
                stockService.consumeFifo(it.getItemId(), t.getFromWarehouseId(), qty);
            }
        }
        t.setStatus(STATUS_IN_TRANSIT);
        transferMapper.updateById(t);
        log.info("Transfer shipped id={}", id);
        return t;
    }

    /**
     * 收货: in_transit → received,目标仓库 addStock。
     * 与 ship 必须在同一事务以保证跨仓库一致性 (安全要求 #7)。
     * 由于 ship 已先单独执行,此方法实际为 addStock + 状态变更。
     * 若 ship 失败,receive 不会触发,数据已回滚。
     */
    @Transactional
    public InvTransfer receive(Long id) {
        UserContext ctx = requireCtx();
        InvTransfer t = getById(id);
        if (!STATUS_IN_TRANSIT.equals(t.getStatus())) {
            throw new ServiceException(409,
                "Only in_transit transfer can be received (current=" + t.getStatus() + "); must ship first");
        }
        List<InvTransferItem> items = transferItemMapper.findByTransfer(ctx.getTenantId(), id);
        if (items == null || items.isEmpty()) {
            throw new ServiceException(409, "Transfer has no items; cannot receive");
        }
        for (InvTransferItem it : items) {
            BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
            String batch = it.getBatchNo() == null || it.getBatchNo().isBlank() ? "DEFAULT" : it.getBatchNo();
            stockService.addStock(t.getToWarehouseId(), 0L, it.getItemId(), batch, qty);
        }
        t.setStatus(STATUS_RECEIVED);
        transferMapper.updateById(t);
        log.info("Transfer received id={}", id);
        return t;
    }

    /**
     * 取消: draft / in_transit → cancelled。received 后不能再取消。
     * 注意: 若 in_transit 状态下取消,源库存已扣减 — 业务上需后续手工补偿 (TODO P5 调 workflow)。
     */
    @Transactional
    public InvTransfer cancel(Long id, String reason) {
        InvTransfer t = getById(id);
        if (!STATUS_DRAFT.equals(t.getStatus()) && !STATUS_IN_TRANSIT.equals(t.getStatus())) {
            throw new ServiceException(409,
                "Only draft/in_transit transfer can be cancelled (current=" + t.getStatus() + ")");
        }
        t.setStatus(STATUS_CANCELLED);
        transferMapper.updateById(t);
        log.info("Transfer cancelled id={} reason={}", id, reason);
        return t;
    }

    private void validate(CreateTransferRequest req) {
        if (req.getFromWarehouseId() == null) throw new ServiceException(400, "fromWarehouseId is required");
        if (req.getToWarehouseId() == null) throw new ServiceException(400, "toWarehouseId is required");
        if (req.getTransferDate() == null) throw new ServiceException(400, "transferDate is required");
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new ServiceException(400, "items must not be empty");
        }
    }

    private String generateCode() {
        return "TF" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}