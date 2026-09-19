package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.SaveWarehouseRequest;
import com.lumen.inventory.entity.InvLocation;
import com.lumen.inventory.entity.InvWarehouse;
import com.lumen.inventory.mapper.InvLocationMapper;
import com.lumen.inventory.mapper.InvWarehouseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 仓库 CRUD + 库位关联查询。
 *
 * <p>安全要点:</p>
 * <ul>
 *   <li>Tenant 隔离: 所有写操作第一行 {@link #requireCtx()} 校验。</li>
 *   <li>跨租户 404: getById 不匹配 → 404,避免存在性泄漏。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private final InvWarehouseMapper warehouseMapper;
    private final InvLocationMapper locationMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<InvWarehouse> page(int pageNum, int pageSize, String keyword, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<InvWarehouse>().orderByDesc(InvWarehouse::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(InvWarehouse::getCode, keyword).or().like(InvWarehouse::getName, keyword));
        }
        if (status != null && !status.isBlank()) w.eq(InvWarehouse::getStatus, status);
        return warehouseMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public InvWarehouse getById(Long id) {
        UserContext ctx = requireCtx();
        InvWarehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new ServiceException(404, "Warehouse not found: " + id);
        if (!w.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Warehouse not found: " + id);
        }
        return w;
    }

    @Transactional
    public InvWarehouse create(SaveWarehouseRequest req) {
        UserContext ctx = requireCtx();
        validate(req);
        if (warehouseMapper.findByCode(ctx.getTenantId(), req.getCode()) != null) {
            throw new ServiceException(409, "Warehouse code already exists: " + req.getCode());
        }
        InvWarehouse w = new InvWarehouse();
        w.setTenantId(ctx.getTenantId());
        w.setCode(req.getCode());
        w.setName(req.getName());
        w.setAddress(req.getAddress());
        w.setManagerId(req.getManagerId());
        w.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        try {
            warehouseMapper.insert(w);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Warehouse code conflict", ex);
        }
        log.info("Warehouse created id={} code={}", w.getId(), w.getCode());
        return w;
    }

    @Transactional
    public InvWarehouse update(Long id, SaveWarehouseRequest req) {
        InvWarehouse existing = getById(id);
        if (req.getName() != null && !req.getName().isBlank()) existing.setName(req.getName());
        if (req.getAddress() != null) existing.setAddress(req.getAddress());
        if (req.getManagerId() != null) existing.setManagerId(req.getManagerId());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        warehouseMapper.updateById(existing);
        return existing;
    }

    /**
     * 列出仓库下所有库位。
     */
    public List<InvLocation> listLocations(Long warehouseId) {
        UserContext ctx = requireCtx();
        // 校验仓库存在 + 同租户 (避免泄漏)
        getById(warehouseId);
        return locationMapper.findByWarehouse(ctx.getTenantId(), warehouseId);
    }

    private void validate(SaveWarehouseRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
    }
}