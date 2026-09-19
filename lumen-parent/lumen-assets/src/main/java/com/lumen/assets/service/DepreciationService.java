package com.lumen.assets.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstDepreciation;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstDepreciationMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * 月度折旧批量。{@link AssetService#depreciateOne} 做单资产；本服务做全部 in_use 资产。
 *
 * <p>幂等保证：UNIQUE(asset_id, period) — 同一资产同一期不可重复计提。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepreciationService {

    private final AstAssetMapper assetMapper;
    private final AstDepreciationMapper depreciationMapper;
    private final AssetService assetService;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    /**
     * 对所有 in_use 资产计提 period 月折旧。period 形如 "2026-09"。
     */
    @Transactional
    public List<AstDepreciation> runMonthly(String period) {
        requireCtx();
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException(400, "period must be yyyy-MM");
        }
        List<AstAsset> assets = assetMapper.selectList(
            new LambdaQueryWrapper<AstAsset>().eq(AstAsset::getStatus, AssetService.STATUS_IN_USE));
        log.info("Depreciation monthly run period={} assets={}", period, assets.size());
        java.util.List<AstDepreciation> out = new java.util.ArrayList<>();
        for (AstAsset a : assets) {
            // 已计提过该 period → 跳过（保证幂等）
            AstDepreciation dup = depreciationMapper.findByAssetAndPeriod(a.getId(), period);
            if (dup != null) {
                log.debug("Skip already-depreciated asset={} period={}", a.getId(), period);
                continue;
            }
            int monthsElapsed = monthsElapsedFromPurchase(a);
            try {
                AstDepreciation dep = assetService.depreciateOne(a.getId(), period, monthsElapsed);
                out.add(dep);
            } catch (DuplicateKeyException ex) {
                log.warn("Depreciation race lost asset={} period={}", a.getId(), period);
            }
        }
        return out;
    }

    private int monthsElapsedFromPurchase(AstAsset a) {
        if (a.getPurchaseDate() == null) return 1;
        LocalDate end = LocalDate.now().withDayOfMonth(1);
        Period p = Period.between(
            a.getPurchaseDate().withDayOfMonth(1), end);
        int months = p.getYears() * 12 + p.getMonths();
        return Math.max(months, 1);
    }
}