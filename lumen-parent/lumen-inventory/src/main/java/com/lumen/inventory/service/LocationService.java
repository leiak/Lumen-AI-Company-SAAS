package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.SaveLocationRequest;
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
import java.util.Set;

/**
 * 库位 CRUD。
 *
 * <p>type 枚举: storage/picking/receiving/shipping。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    public static final String TYPE_STORAGE = "storage";
    public static final String TYPE_PICKING = "picking";
    public static final String TYPE_RECEIVING = "receiving";
    public static final String TYPE_SHIPPING = "shipping";

    public static final Set<String> ALLOWED_TYPES = Set.of(TYPE_STORAGE, TYPE_PICKING, TYPE_RECEIVING, TYPE_SHIPPING);

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private final InvLocationMapper locationMapper;
    private final InvWarehouseMapper warehouseMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public InvLocation getById(Long id) {
        UserContext ctx = requireCtx();
        InvLocation l = locationMapper.selectById(id);
        if (l == null) throw new ServiceException(404, "Location not found: " + id);
        if (!l.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Location not found: " + id);
        }
        return l;
    }

    public List<InvLocation> findByWarehouse(Long warehouseId) {
        UserContext ctx = requireCtx();
        return locationMapper.findByWarehouse(ctx.getTenantId(), warehouseId);
    }

    @Transactional
    public InvLocation create(SaveLocationRequest req) {
        UserContext ctx = requireCtx();
        validate(req);
        // 校验仓库存在 + 同租户
        InvWarehouse wh = warehouseMapper.selectById(req.getWarehouseId());
        if (wh == null || !wh.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(400, "Invalid warehouseId");
        }
        InvLocation l = new InvLocation();
        l.setTenantId(ctx.getTenantId());
        l.setWarehouseId(req.getWarehouseId());
        l.setCode(req.getCode());
        l.setName(req.getName());
        l.setType(req.getType() == null ? TYPE_STORAGE : req.getType());
        l.setCapacity(req.getCapacity());
        l.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        try {
            locationMapper.insert(l);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Location code already exists in this warehouse", ex);
        }
        log.info("Location created id={} warehouse={} code={}", l.getId(), req.getWarehouseId(), l.getCode());
        return l;
    }

    @Transactional
    public InvLocation update(Long id, SaveLocationRequest req) {
        InvLocation existing = getById(id);
        if (req.getName() != null && !req.getName().isBlank()) existing.setName(req.getName());
        if (req.getType() != null) {
            if (!ALLOWED_TYPES.contains(req.getType())) {
                throw new ServiceException(400, "type must be storage/picking/receiving/shipping");
            }
            existing.setType(req.getType());
        }
        if (req.getCapacity() != null) existing.setCapacity(req.getCapacity());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        locationMapper.updateById(existing);
        return existing;
    }

    private void validate(SaveLocationRequest req) {
        if (req.getWarehouseId() == null) throw new ServiceException(400, "warehouseId is required");
        if (req.getCode() == null || req.getCode().isBlank()) throw new ServiceException(400, "code is required");
        if (req.getName() == null || req.getName().isBlank()) throw new ServiceException(400, "name is required");
        if (req.getType() != null && !ALLOWED_TYPES.contains(req.getType())) {
            throw new ServiceException(400, "type must be storage/picking/receiving/shipping");
        }
    }
}