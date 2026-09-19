package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.CreateInoutRequest;
import com.lumen.inventory.dto.InoutItemDto;
import com.lumen.inventory.entity.InvInout;
import com.lumen.inventory.entity.InvInoutItem;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.mapper.InvInoutItemMapper;
import com.lumen.inventory.mapper.InvInoutMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 出入库单主流程。
 *
 * <p>状态机 (安全要求 #5): draft → confirmed | cancelled。confirmed 后不可改。</p>
 * <p>confirm 按 type 路由:</p>
 * <ul>
 *   <li>type=in → addStock (增加可用库存)</li>
 *   <li>type=out → consumeFifo (按入库时间顺序扣减)</li>
 *   <li>type=transfer → 双仓库 move (源扣 + 目标加)</li>
 * </ul>
 * <p>安全要点:</p>
 * <ul>
 *   <li>type=out 必须 source warehouse 扣减 (安全要求 #13)。</li>
 *   <li>type=transfer 必须 sourceType=transfer (安全要求 #14)。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InoutService {

    public static final String TYPE_IN = "in";
    public static final String TYPE_OUT = "out";
    public static final String TYPE_TRANSFER = "transfer";

    public static final String SOURCE_PURCHASE = "purchase";
    public static final String SOURCE_SALES = "sales";
    public static final String SOURCE_TRANSFER = "transfer";
    public static final String SOURCE_MANUAL = "manual";

    public static final Set<String> ALLOWED_TYPES = Set.of(TYPE_IN, TYPE_OUT, TYPE_TRANSFER);
    public static final Set<String> ALLOWED_SOURCES = Set.of(SOURCE_PURCHASE, SOURCE_SALES, SOURCE_TRANSFER, SOURCE_MANUAL);

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_CANCELLED = "cancelled";

    private final InvInoutMapper inoutMapper;
    private final InvInoutItemMapper inoutItemMapper;
    private final StockService stockService;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<InvInout> page(int pageNum, int pageSize, String type, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<InvInout>().orderByDesc(InvInout::getId);
        if (type != null && !type.isBlank()) w.eq(InvInout::getType, type);
        if (status != null && !status.isBlank()) w.eq(InvInout::getStatus, status);
        return inoutMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public InvInout getById(Long id) {
        UserContext ctx = requireCtx();
        InvInout o = inoutMapper.selectById(id);
        if (o == null) throw new ServiceException(404, "Inout not found: " + id);
        if (!o.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Inout not found: " + id);
        }
        return o;
    }

    public List<InvInoutItem> listItems(Long inoutId) {
        UserContext ctx = requireCtx();
        getById(inoutId);
        return inoutItemMapper.findByInout(ctx.getTenantId(), inoutId);
    }

    @Transactional
    public InvInout create(CreateInoutRequest req) {
        UserContext ctx = requireCtx();
        validate(req);
        InvInout o = new InvInout();
        o.setTenantId(ctx.getTenantId());
        o.setCode(generateCode(req.getType()));
        o.setType(req.getType());
        o.setSourceType(req.getSourceType());
        o.setSourceId(req.getSourceId());
        o.setWarehouseId(req.getWarehouseId());
        o.setTargetWarehouseId(req.getTargetWarehouseId());
        o.setOperatorId(ctx.getUserId());
        o.setInoutDate(req.getInoutDate());
        o.setStatus(STATUS_DRAFT);
        o.setRemark(req.getRemark());
        try {
            inoutMapper.insert(o);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Inout code conflict", ex);
        }
        // 明细
        if (req.getItems() != null) {
            for (InoutItemDto dto : req.getItems()) {
                InvInoutItem it = new InvInoutItem();
                it.setTenantId(ctx.getTenantId());
                it.setInoutId(o.getId());
                it.setItemId(dto.getItemId());
                it.setBatchNo(dto.getBatchNo());
                it.setQuantity(dto.getQuantity());
                it.setUnitPrice(dto.getUnitPrice());
                if (dto.getUnitPrice() != null && dto.getQuantity() != null) {
                    it.setSubtotal(dto.getUnitPrice().multiply(dto.getQuantity()));
                }
                inoutItemMapper.insert(it);
            }
        }
        log.info("Inout created id={} code={} type={} items={}",
            o.getId(), o.getCode(), req.getType(),
            req.getItems() == null ? 0 : req.getItems().size());
        return o;
    }

    /**
     * 确认: draft → confirmed。按 type 自动 addStock / consumeFifo。
     * confirmed 后不可改 (安全要求 #5)。
     */
    @Transactional
    public InvInout confirm(Long id) {
        UserContext ctx = requireCtx();
        InvInout o = getById(id);
        if (!STATUS_DRAFT.equals(o.getStatus())) {
            throw new ServiceException(409, "Only draft inout can be confirmed (current=" + o.getStatus() + ")");
        }
        List<InvInoutItem> items = inoutItemMapper.findByInout(ctx.getTenantId(), id);
        if (items == null || items.isEmpty()) {
            throw new ServiceException(409, "Inout has no items; cannot confirm");
        }
        for (InvInoutItem it : items) {
            BigDecimal qty = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
            switch (o.getType()) {
                case TYPE_IN -> {
                    // 入库: 必须有 locationId (用 item 自身的默认 location — 这里简化存到 inv_stock 时 batch_no 用)
                    // 业务上要求 location_id 在 item 级确认;此处入参未传,使用 0 作默认库位 (P5 接 upstream 业务规则)
                    Long locId = 0L;
                    String batch = it.getBatchNo() == null || it.getBatchNo().isBlank() ? "DEFAULT" : it.getBatchNo();
                    stockService.addStock(o.getWarehouseId(), locId, it.getItemId(), batch, qty);
                }
                case TYPE_OUT -> {
                    String batch = it.getBatchNo() == null || it.getBatchNo().isBlank() ? "DEFAULT" : it.getBatchNo();
                    // FIFO 扣减;若指定了 batch_no 则按 batch_no 精确锁
                    if (it.getBatchNo() != null && !it.getBatchNo().isBlank()) {
                        InvStock existing = stockService.findAvailable(it.getItemId(), o.getWarehouseId())
                            .stream()
                            .filter(s -> batch.equals(s.getBatchNo()))
                            .findFirst()
                            .orElse(null);
                        if (existing == null) {
                            throw new ServiceException(409, "Stock not found for item=" + it.getItemId() + " batch=" + batch);
                        }
                        stockService.consume(existing.getId(), qty);
                    } else {
                        stockService.consumeFifo(it.getItemId(), o.getWarehouseId(), qty);
                    }
                }
                case TYPE_TRANSFER -> {
                    // transfer 类型不在 inout.confirm 处理,统一走 TransferService
                    throw new ServiceException(409,
                        "Transfer type inout should be created via TransferService; use transfer/{id}/ship-receive");
                }
                default -> throw new ServiceException(400, "Unknown type: " + o.getType());
            }
        }
        o.setStatus(STATUS_CONFIRMED);
        inoutMapper.updateById(o);
        log.info("Inout confirmed id={} type={}", id, o.getType());
        return o;
    }

    /**
     * 取消: draft → cancelled。confirmed 后不可取消 (安全要求 #5)。
     */
    @Transactional
    public InvInout cancel(Long id, String reason) {
        InvInout o = getById(id);
        if (!STATUS_DRAFT.equals(o.getStatus())) {
            throw new ServiceException(409,
                "Only draft inout can be cancelled (current=" + o.getStatus() + ")");
        }
        o.setStatus(STATUS_CANCELLED);
        inoutMapper.updateById(o);
        log.info("Inout cancelled id={} reason={}", id, reason);
        return o;
    }

    private void validate(CreateInoutRequest req) {
        if (req.getType() == null || !ALLOWED_TYPES.contains(req.getType())) {
            throw new ServiceException(400, "type must be in/out/transfer");
        }
        if (req.getSourceType() == null || !ALLOWED_SOURCES.contains(req.getSourceType())) {
            throw new ServiceException(400, "sourceType must be purchase/sales/transfer/manual");
        }
        // 安全要求 #14: type=transfer 必须 sourceType=transfer
        if (TYPE_TRANSFER.equals(req.getType()) && !SOURCE_TRANSFER.equals(req.getSourceType())) {
            throw new ServiceException(400, "type=transfer requires sourceType=transfer");
        }
        if (req.getWarehouseId() == null) throw new ServiceException(400, "warehouseId is required");
        if (req.getInoutDate() == null) throw new ServiceException(400, "inoutDate is required");
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new ServiceException(400, "items must not be empty");
        }
    }

    /**
     * 简易 code 生成: TYPE-yyyyMMddHHmmss-tenantId。
     * 注: 高并发场景应在 service 层加 UNIQUE 重试,此处依赖 DB UNIQUE 兜底。
     */
    private String generateCode(String type) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String prefix = switch (type) {
            case TYPE_IN -> "IN";
            case TYPE_OUT -> "OUT";
            case TYPE_TRANSFER -> "TR";
            default -> "INV";
        };
        return prefix + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}