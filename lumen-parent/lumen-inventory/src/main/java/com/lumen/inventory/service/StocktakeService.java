package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.StartStocktakeRequest;
import com.lumen.inventory.dto.StocktakeItemRequest;
import com.lumen.inventory.entity.InvStock;
import com.lumen.inventory.entity.InvStocktake;
import com.lumen.inventory.entity.InvStocktakeItem;
import com.lumen.inventory.mapper.InvStockMapper;
import com.lumen.inventory.mapper.InvStocktakeItemMapper;
import com.lumen.inventory.mapper.InvStocktakeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点单主流程。
 *
 * <p>状态机: planning → in_progress → completed。</p>
 * <p>start(): 创建盘点单 + snapshot 当前库存到 stocktake_item (system_quantity = current)。</p>
 * <p>submitItem(): 录入实际数量,自动计算 diff_quantity。</p>
 * <p>complete(): 必须所有 item 已 submit,汇总 diff_count (安全要求 #9)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeService {

    public static final String STATUS_PLANNING = "planning";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    private final InvStocktakeMapper stocktakeMapper;
    private final InvStocktakeItemMapper stocktakeItemMapper;
    private final InvStockMapper stockMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public InvStocktake getById(Long id) {
        UserContext ctx = requireCtx();
        InvStocktake s = stocktakeMapper.selectById(id);
        if (s == null) throw new ServiceException(404, "Stocktake not found: " + id);
        if (!s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stocktake not found: " + id);
        }
        return s;
    }

    public IPage<InvStocktake> page(int pageNum, int pageSize, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<InvStocktake>().orderByDesc(InvStocktake::getId);
        if (status != null && !status.isBlank()) w.eq(InvStocktake::getStatus, status);
        return stocktakeMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public List<InvStocktakeItem> listItems(Long stocktakeId) {
        UserContext ctx = requireCtx();
        getById(stocktakeId);
        return stocktakeItemMapper.findByStocktake(ctx.getTenantId(), stocktakeId);
    }

    /**
     * 启动盘点:创建盘点单 + snapshot 仓库当前库存 → stocktake_item。
     */
    @Transactional
    public InvStocktake start(StartStocktakeRequest req) {
        UserContext ctx = requireCtx();
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        InvStocktake s = new InvStocktake();
        s.setTenantId(ctx.getTenantId());
        s.setCode(req.getCode());
        s.setWarehouseId(req.getWarehouseId());
        s.setPeriod(req.getPeriod());
        s.setPlannedAt(LocalDateTime.now());
        s.setStatus(STATUS_IN_PROGRESS);
        s.setDiffCount(0);
        s.setOperatorId(ctx.getUserId());
        try {
            stocktakeMapper.insert(s);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Stocktake code conflict", ex);
        }
        // snapshot: 拉该仓库的所有 stock
        List<InvStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<InvStock>()
            .eq(InvStock::getWarehouseId, req.getWarehouseId()));
        for (InvStock st : stocks) {
            InvStocktakeItem it = new InvStocktakeItem();
            it.setTenantId(ctx.getTenantId());
            it.setStocktakeId(s.getId());
            it.setItemId(st.getItemId());
            it.setWarehouseId(st.getWarehouseId());
            it.setBatchNo(st.getBatchNo());
            it.setSystemQuantity(st.getQuantity());
            it.setActualQuantity(null);
            it.setDiffQuantity(null);
            it.setSubmitted(0);
            stocktakeItemMapper.insert(it);
        }
        log.info("Stocktake started id={} code={} wh={} period={} items={}",
            s.getId(), req.getCode(), req.getWarehouseId(), req.getPeriod(), stocks.size());
        return s;
    }

    /**
     * 提交单条盘点结果。自动计算 diff_quantity。
     */
    @Transactional
    public InvStocktakeItem submitItem(StocktakeItemRequest req) {
        UserContext ctx = requireCtx();
        if (req.getItemId() == null) throw new ServiceException(400, "itemId is required");
        InvStocktakeItem it = stocktakeItemMapper.selectById(req.getItemId());
        if (it == null) throw new ServiceException(404, "Stocktake item not found: " + req.getItemId());
        if (!it.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stocktake item not found: " + req.getItemId());
        }
        InvStocktake parent = getById(it.getStocktakeId());
        if (STATUS_COMPLETED.equals(parent.getStatus())) {
            throw new ServiceException(409, "Stocktake already completed");
        }
        BigDecimal actual = req.getActualQuantity() == null ? BigDecimal.ZERO : req.getActualQuantity();
        it.setActualQuantity(actual);
        BigDecimal sys = it.getSystemQuantity() == null ? BigDecimal.ZERO : it.getSystemQuantity();
        it.setDiffQuantity(actual.subtract(sys));
        it.setSubmitted(1);
        stocktakeItemMapper.updateById(it);
        log.info("Stocktake item submitted id={} sys={} actual={} diff={}",
            it.getId(), sys, actual, it.getDiffQuantity());
        return it;
    }

    /**
     * 完成盘点:校验所有 item 已 submit (安全要求 #9),汇总 diff_count。
     * TODO P5: 生成 adjust 工单 (workflow)。
     */
    @Transactional
    public InvStocktake complete(Long id) {
        InvStocktake s = getById(id);
        if (STATUS_COMPLETED.equals(s.getStatus())) {
            throw new ServiceException(409, "Stocktake already completed");
        }
        UserContext ctx = UserContextHolder.get();
        List<InvStocktakeItem> items = stocktakeItemMapper.findByStocktake(ctx.getTenantId(), id);
        if (items == null || items.isEmpty()) {
            throw new ServiceException(409, "Stocktake has no items");
        }
        int diff = 0;
        for (InvStocktakeItem it : items) {
            if (it.getSubmitted() == null || it.getSubmitted() != 1) {
                throw new ServiceException(409,
                    "All items must be submitted before completion (item=" + it.getId() + ")");
            }
            if (it.getDiffQuantity() != null && it.getDiffQuantity().compareTo(BigDecimal.ZERO) != 0) {
                diff++;
            }
        }
        s.setDiffCount(diff);
        s.setStatus(STATUS_COMPLETED);
        s.setCompletedAt(LocalDateTime.now());
        stocktakeMapper.updateById(s);
        // TODO P5: 生成 adjust 工单 (workflow)
        log.info("Stocktake completed id={} diffCount={}", id, diff);
        return s;
    }
}