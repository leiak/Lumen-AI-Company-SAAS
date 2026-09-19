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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产统计报表。供 /assets/report/* 使用。
 * 输出基本聚合：按分类汇总金额 + 按部门汇总金额 + 按期间汇总折旧。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AstAssetMapper assetMapper;
    private final AstDepreciationMapper depreciationMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    /** 按 category 汇总：count / originalValue / currentValue。 */
    public List<Map<String, Object>> categorySummary() {
        requireCtx();
        List<AstAsset> all = assetMapper.selectList(
            new LambdaQueryWrapper<AstAsset>().orderByAsc(AstAsset::getCategoryId));
        Map<Long, Map<String, Object>> bucket = new LinkedHashMap<>();
        for (AstAsset a : all) {
            Long cat = a.getCategoryId() == null ? 0L : a.getCategoryId();
            Map<String, Object> row = bucket.computeIfAbsent(cat, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoryId", k);
                m.put("count", 0);
                m.put("originalValue", BigDecimal.ZERO);
                m.put("currentValue", BigDecimal.ZERO);
                return m;
            });
            row.put("count", (Integer) row.get("count") + 1);
            row.put("originalValue",
                ((BigDecimal) row.get("originalValue")).add(safe(a.getOriginalValue())));
            row.put("currentValue",
                ((BigDecimal) row.get("currentValue")).add(safe(a.getCurrentValue())));
        }
        return new ArrayList<>(bucket.values());
    }

    /** 按 dept 汇总。 */
    public List<Map<String, Object>> deptSummary() {
        requireCtx();
        List<AstAsset> all = assetMapper.selectList(
            new LambdaQueryWrapper<AstAsset>().orderByAsc(AstAsset::getDeptId));
        Map<Long, Map<String, Object>> bucket = new LinkedHashMap<>();
        for (AstAsset a : all) {
            Long dept = a.getDeptId() == null ? 0L : a.getDeptId();
            Map<String, Object> row = bucket.computeIfAbsent(dept, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("deptId", k);
                m.put("count", 0);
                m.put("originalValue", BigDecimal.ZERO);
                m.put("currentValue", BigDecimal.ZERO);
                return m;
            });
            row.put("count", (Integer) row.get("count") + 1);
            row.put("originalValue",
                ((BigDecimal) row.get("originalValue")).add(safe(a.getOriginalValue())));
            row.put("currentValue",
                ((BigDecimal) row.get("currentValue")).add(safe(a.getCurrentValue())));
        }
        return new ArrayList<>(bucket.values());
    }

    /** 指定 period 内的折旧合计。 */
    public Map<String, Object> depreciationSummary(String period) {
        requireCtx();
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new ServiceException(400, "period must be yyyy-MM");
        }
        List<AstDepreciation> rows = depreciationMapper.selectList(
            new LambdaQueryWrapper<AstDepreciation>().eq(AstDepreciation::getPeriod, period));
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (AstDepreciation r : rows) {
            if (r.getDepreciationAmount() != null) total = total.add(r.getDepreciationAmount());
            count++;
        }
        Map<String, Object> out = new HashMap<>();
        out.put("period", period);
        out.put("count", count);
        out.put("totalDepreciation", total);
        return out;
    }

    private static BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}