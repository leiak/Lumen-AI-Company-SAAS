package com.lumen.assets.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.assets.dto.StocktakeItemRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstStocktake;
import com.lumen.assets.entity.AstStocktakeItem;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstStocktakeItemMapper;
import com.lumen.assets.mapper.AstStocktakeMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产盘点单。状态机：planning → in_progress → completed。
 * start() 创建盘点单 + 按部门 snapshot 所有资产 → stocktake_item。
 * submitItem() 录入实际状态，计算 diff_type。
 * complete() 必须所有 item 已 submit，否则 409。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeService {

    public static final String STATUS_PLANNING = "planning";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    public static final String DIFF_NONE = "none";
    public static final String DIFF_LOST = "lost";
    public static final String DIFF_EXTRA = "extra";
    public static final String DIFF_MOVED = "moved";
    public static final String DIFF_DAMAGED = "damaged";

    private final AstStocktakeMapper stocktakeMapper;
    private final AstStocktakeItemMapper stocktakeItemMapper;
    private final AstAssetMapper assetMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public AstStocktake getById(Long id) {
        UserContext ctx = requireCtx();
        AstStocktake s = stocktakeMapper.selectById(id);
        if (s == null) throw new ServiceException(404, "Stocktake not found: " + id);
        if (!s.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stocktake not found: " + id);
        }
        return s;
    }

    public List<AstStocktakeItem> findItems(Long stocktakeId) {
        requireCtx();
        return stocktakeItemMapper.findByStocktake(stocktakeId);
    }

    public List<AstStocktake> findByPeriodAndDept(String period, Long deptId) {
        requireCtx();
        return stocktakeMapper.findByPeriodAndDept(period, deptId);
    }

    /**
     * 启动盘点：创建 stocktake + 把部门下的所有资产 snapshot 到 stocktake_item。
     */
    @Transactional
    public AstStocktake start(String code, String period, Long deptId, LocalDateTime plannedAt) {
        UserContext ctx = requireCtx();
        AstStocktake s = new AstStocktake();
        s.setTenantId(ctx.getTenantId());
        s.setCode(code);
        s.setPeriod(period);
        s.setDepartmentId(deptId);
        s.setPlannedAt(plannedAt != null ? plannedAt : LocalDateTime.now());
        s.setStatus(STATUS_IN_PROGRESS);
        s.setDiffCount(0);
        stocktakeMapper.insert(s);

        // snapshot：拉部门下的所有资产
        List<AstAsset> assets = assetMapper.findByDeptAndStatus(deptId, null);
        // findByDeptAndStatus 要求 status 非空，所以走 status IN 列表方式
        if (assets == null || assets.isEmpty()) {
            assets = assetMapper.selectList(new LambdaQueryWrapper<AstAsset>()
                .eq(AstAsset::getDeptId, deptId));
        }
        for (AstAsset a : assets) {
            AstStocktakeItem item = new AstStocktakeItem();
            item.setTenantId(ctx.getTenantId());
            item.setStocktakeId(s.getId());
            item.setAssetId(a.getId());
            item.setExpectedStatus(a.getStatus());
            item.setDiffType(DIFF_NONE);
            stocktakeItemMapper.insert(item);
        }
        log.info("Stocktake started id={} code={} period={} dept={} items={}",
            s.getId(), code, period, deptId, assets == null ? 0 : assets.size());
        return s;
    }

    /**
     * 提交单条盘点结果。校验 item 存在；写入实际状态、计算 diff_type。
     */
    @Transactional
    public AstStocktakeItem submitItem(StocktakeItemRequest req) {
        UserContext ctx = requireCtx();
        AstStocktakeItem item = stocktakeItemMapper.selectById(req.getItemId());
        if (item == null) {
            throw new ServiceException(404, "Stocktake item not found: " + req.getItemId());
        }
        if (!item.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Stocktake item not found: " + req.getItemId());
        }
        AstStocktake parent = getById(item.getStocktakeId());
        if (STATUS_COMPLETED.equals(parent.getStatus())) {
            throw new ServiceException(409, "Stocktake already completed");
        }
        item.setActualStatus(req.getActualStatus());
        item.setActualLocation(req.getActualLocation());
        item.setNote(req.getNote());
        item.setDiffType(computeDiffType(item.getExpectedStatus(), req.getActualStatus()));
        stocktakeItemMapper.updateById(item);
        return item;
    }

    /**
     * 完成盘点：必须所有 item 都已 submit 实际状态（actualStatus 非空）。
     * 汇总 diff_count，生成 adjust 工单 TODO（占位 log）。
     */
    @Transactional
    public AstStocktake complete(Long id) {
        AstStocktake s = getById(id);
        if (STATUS_COMPLETED.equals(s.getStatus())) {
            throw new ServiceException(409, "Stocktake already completed");
        }
        List<AstStocktakeItem> items = stocktakeItemMapper.findByStocktake(id);
        if (items == null || items.isEmpty()) {
            throw new ServiceException(409, "Stocktake has no items");
        }
        int diff = 0;
        for (AstStocktakeItem it : items) {
            if (it.getActualStatus() == null || it.getActualStatus().isBlank()) {
                throw new ServiceException(409,
                    "All items must be submitted before completion (item=" + it.getId() + ")");
            }
            if (!DIFF_NONE.equals(it.getDiffType())) diff++;
        }
        s.setDiffCount(diff);
        s.setStatus(STATUS_COMPLETED);
        s.setCompletedAt(LocalDateTime.now());
        stocktakeMapper.updateById(s);
        // TODO: 生成 adjust 工单（后续接入 workflow）
        log.info("Stocktake completed id={} diffCount={}", id, diff);
        return s;
    }

    private String computeDiffType(String expected, String actual) {
        if (expected == null || actual == null) return DIFF_NONE;
        if (expected.equals(actual)) return DIFF_NONE;
        // 简化规则：丢失/损坏/位置变更都视为异常
        if (AssetService.STATUS_SCRAPPED.equals(actual)) return DIFF_DAMAGED;
        if (AssetService.STATUS_IN_STOCK.equals(actual) && AssetService.STATUS_IN_USE.equals(expected)) return DIFF_MOVED;
        return DIFF_LOST;
    }
}