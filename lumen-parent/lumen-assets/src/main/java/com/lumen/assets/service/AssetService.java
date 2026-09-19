package com.lumen.assets.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.assets.dto.SaveAssetRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstCategory;
import com.lumen.assets.entity.AstDepreciation;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstCategoryMapper;
import com.lumen.assets.mapper.AstDepreciationMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产 CRUD + 报废/恢复 + 折旧计提。
 *
 * <p>安全要点：</p>
 * <ul>
 *   <li>Tenant 隔离：所有写操作第一行 {@link #requireCtx()} 校验。</li>
 *   <li>原值 immutable：{@link #update} 显式忽略 {@code originalValue}。</li>
 *   <li>状态机：in_use ↔ maintenance 允许；in_use → scrapped 终态；scrapped 不可恢复（{@link #restore} 拒绝）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    public static final String STATUS_IN_STOCK = "in_stock";
    public static final String STATUS_IN_USE = "in_use";
    public static final String STATUS_MAINTENANCE = "maintenance";
    public static final String STATUS_SCRAPPED = "scrapped";

    public static final String METHOD_STRAIGHT_LINE = "straight_line";
    public static final String METHOD_DOUBLE_DECLINING = "double_declining";
    public static final String METHOD_SUM_OF_YEARS = "sum_of_years";

    private final AstAssetMapper assetMapper;
    private final AstCategoryMapper categoryMapper;
    private final AstDepreciationMapper depreciationMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<AstAsset> page(int pageNum, int pageSize, String keyword,
                                 Long categoryId, String status, Long deptId) {
        requireCtx();
        var w = new LambdaQueryWrapper<AstAsset>().orderByDesc(AstAsset::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(AstAsset::getCode, keyword).or().like(AstAsset::getName, keyword));
        }
        if (categoryId != null) w.eq(AstAsset::getCategoryId, categoryId);
        if (status != null && !status.isBlank()) w.eq(AstAsset::getStatus, status);
        if (deptId != null) w.eq(AstAsset::getDeptId, deptId);
        return assetMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public AstAsset getById(Long id) {
        UserContext ctx = requireCtx();
        AstAsset a = assetMapper.selectById(id);
        if (a == null) throw new ServiceException(404, "Asset not found: " + id);
        if (!a.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Asset not found: " + id);
        }
        return a;
    }

    @Transactional
    public AstAsset create(SaveAssetRequest req) {
        UserContext ctx = requireCtx();
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        if (req.getCategoryId() == null) {
            throw new ServiceException(400, "categoryId is required");
        }
        if (req.getOriginalValue() == null) {
            throw new ServiceException(400, "originalValue is required");
        }
        // category 校验存在 + 同租户
        AstCategory cat = categoryMapper.selectById(req.getCategoryId());
        if (cat == null || !cat.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(400, "Invalid categoryId");
        }
        AstAsset toCreate = new AstAsset();
        toCreate.setTenantId(ctx.getTenantId());
        toCreate.setCode(req.getCode());
        toCreate.setName(req.getName());
        toCreate.setCategoryId(req.getCategoryId());
        toCreate.setOriginalValue(req.getOriginalValue());
        toCreate.setCurrentValue(req.getOriginalValue()); // 初始 = 原值
        toCreate.setDepreciationMethod(req.getDepreciationMethod() != null
            ? req.getDepreciationMethod()
            : cat.getDepreciationMethodDefault());
        toCreate.setUsefulLifeMonths(req.getUsefulLifeMonths() != null
            ? req.getUsefulLifeMonths()
            : cat.getUsefulLifeDefault());
        toCreate.setSalvageValue(req.getSalvageValue() != null
            ? req.getSalvageValue()
            : BigDecimal.ZERO);
        toCreate.setPurchaseDate(req.getPurchaseDate());
        toCreate.setDeptId(req.getDeptId());
        toCreate.setCustodianId(req.getCustodianId());
        toCreate.setStatus(req.getStatus() != null ? req.getStatus() : STATUS_IN_STOCK);
        toCreate.setQrCode(req.getQrCode());
        toCreate.setImageUrl(req.getImageUrl());
        try {
            assetMapper.insert(toCreate);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Asset code conflict", ex);
        }
        log.info("Asset created id={} code={}", toCreate.getId(), toCreate.getCode());
        return toCreate;
    }

    /**
     * 更新资产。{@code originalValue} 原值永远不变（immutable）—— 即便客户端传入也忽略。
     */
    @Transactional
    public AstAsset update(Long id, SaveAssetRequest req) {
        AstAsset existing = getById(id);
        if (STATUS_SCRAPPED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Scrapped asset is immutable");
        }
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getCategoryId() != null) existing.setCategoryId(req.getCategoryId());
        // originalValue 显式忽略 — 不允许修改
        if (req.getDepreciationMethod() != null) existing.setDepreciationMethod(req.getDepreciationMethod());
        if (req.getUsefulLifeMonths() != null) existing.setUsefulLifeMonths(req.getUsefulLifeMonths());
        if (req.getSalvageValue() != null) existing.setSalvageValue(req.getSalvageValue());
        if (req.getPurchaseDate() != null) existing.setPurchaseDate(req.getPurchaseDate());
        if (req.getDeptId() != null) existing.setDeptId(req.getDeptId());
        if (req.getCustodianId() != null) existing.setCustodianId(req.getCustodianId());
        if (req.getStatus() != null) {
            assertStatusTransition(existing.getStatus(), req.getStatus());
            existing.setStatus(req.getStatus());
        }
        if (req.getQrCode() != null) existing.setQrCode(req.getQrCode());
        if (req.getImageUrl() != null) existing.setImageUrl(req.getImageUrl());
        assetMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        AstAsset existing = getById(id);
        if (STATUS_IN_USE.equals(existing.getStatus())) {
            throw new ServiceException(409, "Cannot delete in-use asset; scrap it first");
        }
        assetMapper.deleteById(id);
    }

    // ---------------------------------------------------------------
    // 状态机：报废 / 恢复
    // ---------------------------------------------------------------

    /**
     * 报废资产：仅允许 in_use / in_stock / maintenance → scrapped。
     */
    @Transactional
    public AstAsset scrapped(Long id, String reason) {
        AstAsset existing = getById(id);
        if (STATUS_SCRAPPED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Asset already scrapped");
        }
        existing.setStatus(STATUS_SCRAPPED);
        assetMapper.updateById(existing);
        log.info("Asset scrapped id={} reason={}", id, reason);
        return existing;
    }

    /**
     * 恢复资产：scrapped 是终态，不允许恢复。
     */
    @Transactional
    public AstAsset restore(Long id) {
        AstAsset existing = getById(id);
        if (STATUS_SCRAPPED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Scrapped asset cannot be restored");
        }
        log.info("Asset restore (no-op state change) id={}", id);
        return existing;
    }

    private void assertStatusTransition(String from, String to) {
        if (from == null || to == null || from.equals(to)) return;
        if (STATUS_SCRAPPED.equals(from)) {
            throw new ServiceException(409, "Scrapped asset is terminal; cannot transition to " + to);
        }
        // in_use ↔ maintenance 允许；其它变更也允许 in_stock ↔ in_use
        boolean allowed = switch (from) {
            case STATUS_IN_STOCK -> STATUS_IN_USE.equals(to) || STATUS_MAINTENANCE.equals(to) || STATUS_SCRAPPED.equals(to);
            case STATUS_IN_USE -> STATUS_IN_STOCK.equals(to) || STATUS_MAINTENANCE.equals(to) || STATUS_SCRAPPED.equals(to);
            case STATUS_MAINTENANCE -> STATUS_IN_USE.equals(to) || STATUS_IN_STOCK.equals(to) || STATUS_SCRAPPED.equals(to);
            default -> false;
        };
        if (!allowed) {
            throw new ServiceException(409, "Invalid asset status transition: " + from + " -> " + to);
        }
    }

    // ---------------------------------------------------------------
    // 折旧
    // ---------------------------------------------------------------

    /**
     * 直线法折旧：月折旧额 = (原值 - 残值) / 使用月数。
     * 返回本期的折旧金额；不写入数据库（runMonthly 负责批量写）。
     */
    public BigDecimal computeStraightLine(AstAsset asset, int monthsElapsed) {
        BigDecimal depreciable = asset.getOriginalValue().subtract(
            asset.getSalvageValue() == null ? BigDecimal.ZERO : asset.getSalvageValue());
        if (depreciable.signum() <= 0) return BigDecimal.ZERO;
        Integer life = asset.getUsefulLifeMonths();
        if (life == null || life <= 0) {
            throw new ServiceException(400, "usefulLifeMonths must be positive");
        }
        BigDecimal perMonth = depreciable.divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
        BigDecimal cap = perMonth.multiply(BigDecimal.valueOf(monthsElapsed));
        if (cap.compareTo(depreciable) > 0) cap = depreciable;
        return cap.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 对单资产在指定 period 计提折旧（period 形如 "2026-09"）。
     * 已计提过该 period → 409（保证 UNIQUE(asset_id, period) 不被破坏）。
     */
    @Transactional
    public AstDepreciation depreciateOne(Long assetId, String period, int monthsElapsed) {
        AstAsset asset = getById(assetId);
        if (!STATUS_IN_USE.equals(asset.getStatus())) {
            throw new ServiceException(409, "Only in_use asset can be depreciated");
        }
        AstDepreciation existing = depreciationMapper.findByAssetAndPeriod(assetId, period);
        if (existing != null) {
            throw new ServiceException(409, "Depreciation already calculated for asset=" + assetId + " period=" + period);
        }
        BigDecimal amount;
        String method = asset.getDepreciationMethod() == null ? METHOD_STRAIGHT_LINE : asset.getDepreciationMethod();
        switch (method) {
            case METHOD_STRAIGHT_LINE -> amount = computeStraightLine(asset, monthsElapsed);
            case METHOD_DOUBLE_DECLINING -> amount = computeDoubleDeclining(asset, monthsElapsed);
            case METHOD_SUM_OF_YEARS -> amount = computeSumOfYears(asset, monthsElapsed);
            default -> throw new ServiceException(400, "Unknown depreciation method: " + method);
        }
        AstDepreciation dep = new AstDepreciation();
        dep.setTenantId(asset.getTenantId());
        dep.setAssetId(assetId);
        dep.setPeriod(period);
        dep.setDepreciationAmount(amount);
        // 累计折旧 = 此前累计 + 本期
        BigDecimal prevAccumulated = sumAccumulated(assetId);
        dep.setAccumulatedAmount(prevAccumulated.add(amount));
        BigDecimal newNet = asset.getOriginalValue().subtract(dep.getAccumulatedAmount());
        if (newNet.compareTo(asset.getSalvageValue() == null ? BigDecimal.ZERO : asset.getSalvageValue()) < 0) {
            newNet = asset.getSalvageValue() == null ? BigDecimal.ZERO : asset.getSalvageValue();
        }
        dep.setNetValue(newNet);
        dep.setCalculatedAt(LocalDateTime.now());
        dep.setMethod(method);
        depreciationMapper.insert(dep);
        // 更新资产净值
        asset.setCurrentValue(newNet);
        assetMapper.updateById(asset);
        log.info("Depreciation calculated asset={} period={} amount={}", assetId, period, amount);
        return dep;
    }

    private BigDecimal sumAccumulated(Long assetId) {
        List<AstDepreciation> rows = depreciationMapper.selectList(
            new LambdaQueryWrapper<AstDepreciation>().eq(AstDepreciation::getAssetId, assetId));
        BigDecimal sum = BigDecimal.ZERO;
        for (AstDepreciation r : rows) {
            if (r.getDepreciationAmount() != null) sum = sum.add(r.getDepreciationAmount());
        }
        return sum;
    }

    /**
     * 双倍余额递减法（月）。双倍 = 2 / usefulLifeMonths * 期初净值。
     * 末两年切换直线以避免残值为负。
     */
    BigDecimal computeDoubleDeclining(AstAsset asset, int monthsElapsed) {
        Integer life = asset.getUsefulLifeMonths();
        if (life == null || life <= 0) {
            throw new ServiceException(400, "usefulLifeMonths must be positive");
        }
        BigDecimal rate = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(life), 8, RoundingMode.HALF_UP);
        // 末两月切换直线
        BigDecimal salvage = asset.getSalvageValue() == null ? BigDecimal.ZERO : asset.getSalvageValue();
        BigDecimal net = asset.getOriginalValue();
        BigDecimal accum = BigDecimal.ZERO;
        for (int m = 1; m <= monthsElapsed; m++) {
            BigDecimal periodAmount;
            if (m > life - 2) {
                BigDecimal remainingLife = BigDecimal.valueOf(life - m + 1);
                BigDecimal base = net.subtract(salvage);
                periodAmount = base.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : base.divide(remainingLife, 2, RoundingMode.HALF_UP);
            } else {
                periodAmount = net.subtract(salvage).multiply(rate).setScale(2, RoundingMode.HALF_UP);
                if (net.subtract(periodAmount).compareTo(salvage) < 0) {
                    periodAmount = net.subtract(salvage);
                }
            }
            accum = accum.add(periodAmount);
            net = net.subtract(periodAmount);
            if (net.compareTo(salvage) < 0) net = salvage;
        }
        return accum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 年数总和法。第 m 期折旧 = (原值 - 残值) * (usefulLifeMonths/12 - m + 1) / sum。
     * sum = 1+2+...+years（年数）。按月等分。
     */
    BigDecimal computeSumOfYears(AstAsset asset, int monthsElapsed) {
        Integer lifeMonths = asset.getUsefulLifeMonths();
        if (lifeMonths == null || lifeMonths <= 0) {
            throw new ServiceException(400, "usefulLifeMonths must be positive");
        }
        int years = (lifeMonths + 11) / 12;
        if (years <= 0) years = 1;
        BigDecimal sum = BigDecimal.valueOf((long) years * (years + 1) / 2);
        BigDecimal depreciable = asset.getOriginalValue().subtract(
            asset.getSalvageValue() == null ? BigDecimal.ZERO : asset.getSalvageValue());
        if (depreciable.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal accum = BigDecimal.ZERO;
        for (int m = 1; m <= monthsElapsed; m++) {
            int yearIndex = (m - 1) / 12 + 1; // 1-based
            int remainingYears = Math.max(years - yearIndex + 1, 1);
            BigDecimal monthFraction = BigDecimal.ONE.divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
            BigDecimal periodAmount = depreciable.multiply(BigDecimal.valueOf(remainingYears))
                .divide(sum, 8, RoundingMode.HALF_UP)
                .multiply(monthFraction)
                .setScale(2, RoundingMode.HALF_UP);
            if (accum.add(periodAmount).compareTo(depreciable) > 0) {
                periodAmount = depreciable.subtract(accum);
            }
            accum = accum.add(periodAmount);
            if (accum.compareTo(depreciable) >= 0) break;
        }
        return accum.setScale(2, RoundingMode.HALF_UP);
    }
}