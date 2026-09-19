package com.lumen.bi.service;

import com.lumen.bi.entity.BiDataset;
import com.lumen.bi.mapper.BiDatasetMapper;
import com.lumen.bi.sql.SqlGuard;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集服务。CRUD + preview 执行 SQL 返回分页数据。
 *
 * <p>安全要求 #1: BI 服务执行跨服务 SQL 是 read-only, 通过 JDBC 直接连 lumen_db;
 * 安全要求 #10: parseDefinition/model 中的 sql 必须 SELECT-only。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private final BiDatasetMapper datasetMapper;

    /**
     * BI reader JDBC (TODO P5: 切换到 bi_reader 只读账号)。
     * 用 @Qualifier 区分默认 JdbcTemplate 避免冲突 (虽然这里没有冲突)。
     */
    @Qualifier("jdbcTemplate")
    private final JdbcTemplate biJdbc;

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    public BiDataset get(Long id) {
        UserContext ctx = requireUserContext();
        BiDataset d = datasetMapper.selectById(id);
        if (d == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(d.getTenantId()))) {
            throw new ServiceException(404, "Dataset not found: " + id);
        }
        return d;
    }

    public List<BiDataset> list() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return datasetMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BiDataset>()
                .eq(BiDataset::getTenantId, ctx.getTenantId())
                .eq(BiDataset::getDeleted, 0)
                .orderByDesc(BiDataset::getId));
    }

    public List<BiDataset> findActive() {
        UserContext ctx = requireUserContext();
        List<BiDataset> all = datasetMapper.findActive();
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(d -> ctx.getTenantId().equals(d.getTenantId()))
            .toList();
    }

    public List<BiDataset> findBySourceType(String sourceType) {
        UserContext ctx = requireUserContext();
        List<BiDataset> all = datasetMapper.findBySourceType(sourceType);
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(d -> ctx.getTenantId().equals(d.getTenantId()))
            .toList();
    }

    // ---------------------------------------------------------------
    // write
    // ---------------------------------------------------------------

    @Transactional
    public BiDataset save(BiDataset req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        // 校验 model 中 sql 若存在则只读 SELECT
        if (req.getModel() != null) {
            Object sql = req.getModel().get("sql");
            if (sql instanceof String s) {
                SqlGuard.validateSelectOnly(s);
            }
        }
        BiDataset existing = datasetMapper.findByCodeAndTenant(req.getCode(), ctx.getTenantId());
        if (existing != null) {
            if (req.getName() != null) existing.setName(req.getName());
            if (req.getSourceType() != null) existing.setSourceType(req.getSourceType());
            if (req.getModel() != null) existing.setModel(req.getModel());
            existing.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
            datasetMapper.updateById(existing);
            return existing;
        }
        BiDataset d = new BiDataset();
        d.setTenantId(ctx.getTenantId());
        d.setCode(req.getCode());
        d.setName(req.getName());
        d.setSourceType(req.getSourceType() == null ? "sql" : req.getSourceType());
        d.setModel(req.getModel());
        d.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        datasetMapper.insert(d);
        return d;
    }

    // ---------------------------------------------------------------
    // preview — 执行 SQL 返回数据
    // ---------------------------------------------------------------

    /**
     * 安全要求 #15: pageSize 1..200。
     * 安全要求 #1: 自动注入 tenant 过滤。
     */
    public Map<String, Object> preview(String datasetCode, int page, int pageSize) {
        if (page < 1) throw new ServiceException(400, "page must be >= 1");
        if (pageSize < 1 || pageSize > 200) {
            throw new ServiceException(400, "pageSize must be 1..200");
        }
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        BiDataset dataset = datasetMapper.findByCodeAndTenant(datasetCode, ctx.getTenantId());
        if (dataset == null) {
            throw new ServiceException(404, "Dataset not found: " + datasetCode);
        }
        if (!STATUS_ACTIVE.equals(dataset.getStatus())) {
            throw new ServiceException(409, "Dataset is not active: " + datasetCode);
        }
        Map<String, Object> model = dataset.getModel();
        if (model == null) {
            throw new ServiceException(409, "Dataset has no model: " + datasetCode);
        }
        String sql = (String) model.get("sql");
        if (sql == null || sql.isBlank()) {
            throw new ServiceException(409, "Dataset has no SQL: " + datasetCode);
        }
        SqlGuard.validateSelectOnly(sql);
        sql = SqlGuard.injectTenantFilter(sql);
        sql = SqlGuard.enforceLimit(sql, pageSize);

        int offset = (page - 1) * pageSize;
        // offset 在 LIMIT 后追加
        String pagedSql = sql + " OFFSET " + offset;
        List<Map<String, Object>> rows = biJdbc.queryForList(pagedSql, ctx.getTenantId());

        Map<String, Object> result = new HashMap<>();
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("rows", rows);
        return result;
    }

    @Transactional
    public void delete(Long id) {
        BiDataset d = get(id);
        datasetMapper.deleteById(d.getId());
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}