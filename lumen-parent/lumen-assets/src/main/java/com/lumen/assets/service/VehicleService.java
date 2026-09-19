package com.lumen.assets.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.assets.entity.AstVehicle;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstVehicleMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 车辆扩展（CRUD + 里程记录 + 维护预约）。
 *
 * <p>安全 #9: 里程只能递增 — {@link #recordMileage} 校验新值 ≥ 现值。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final AstVehicleMapper vehicleMapper;
    private final AstAssetMapper assetMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public AstVehicle getById(Long id) {
        UserContext ctx = requireCtx();
        AstVehicle v = vehicleMapper.selectById(id);
        if (v == null) throw new ServiceException(404, "Vehicle not found: " + id);
        if (!v.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Vehicle not found: " + id);
        }
        return v;
    }

    public AstVehicle findByPlate(String plateNo) {
        requireCtx();
        AstVehicle v = vehicleMapper.findByPlate(plateNo);
        if (v == null) throw new ServiceException(404, "Vehicle not found by plate: " + plateNo);
        return v;
    }

    public IPage<AstVehicle> page(int pageNum, int pageSize, String keyword) {
        requireCtx();
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AstVehicle>()
            .orderByDesc(AstVehicle::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(AstVehicle::getPlateNo, keyword)
                .or().like(AstVehicle::getVehicleType, keyword));
        }
        return vehicleMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    @Transactional
    public AstVehicle create(AstVehicle req) {
        UserContext ctx = requireCtx();
        if (req.getAssetId() == null) {
            throw new ServiceException(400, "assetId is required");
        }
        if (req.getPlateNo() == null || req.getPlateNo().isBlank()) {
            throw new ServiceException(400, "plateNo is required");
        }
        AstVehicle toCreate = new AstVehicle();
        toCreate.setTenantId(ctx.getTenantId());
        toCreate.setAssetId(req.getAssetId());
        toCreate.setPlateNo(req.getPlateNo());
        toCreate.setVehicleType(req.getVehicleType());
        toCreate.setCapacity(req.getCapacity());
        toCreate.setCurrentMileage(req.getCurrentMileage() == null ? 0L : req.getCurrentMileage());
        toCreate.setNextMaintenanceMileage(req.getNextMaintenanceMileage());
        try {
            vehicleMapper.insert(toCreate);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Vehicle plateNo conflict", ex);
        }
        return toCreate;
    }

    @Transactional
    public AstVehicle update(Long id, AstVehicle req) {
        AstVehicle existing = getById(id);
        if (req.getVehicleType() != null) existing.setVehicleType(req.getVehicleType());
        if (req.getCapacity() != null) existing.setCapacity(req.getCapacity());
        if (req.getNextMaintenanceMileage() != null) {
            existing.setNextMaintenanceMileage(req.getNextMaintenanceMileage());
        }
        if (req.getLastMaintenanceAt() != null) existing.setLastMaintenanceAt(req.getLastMaintenanceAt());
        // 注意：currentMileage 走 recordMileage，不允许通过 update 直接改
        vehicleMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        vehicleMapper.deleteById(id);
    }

    /**
     * 记录里程。新值必须 ≥ 现值，否则 409。
     */
    @Transactional
    public AstVehicle recordMileage(Long id, Long mileage, LocalDate recordedAt) {
        if (mileage == null || mileage < 0) {
            throw new ServiceException(400, "mileage must be non-negative");
        }
        AstVehicle existing = getById(id);
        Long current = existing.getCurrentMileage() == null ? 0L : existing.getCurrentMileage();
        if (mileage < current) {
            throw new ServiceException(409,
                "mileage must be monotonically increasing (current=" + current + ", given=" + mileage + ")");
        }
        existing.setCurrentMileage(mileage);
        vehicleMapper.updateById(existing);
        log.info("Vehicle mileage recorded id={} from={} to={} at={}",
            id, current, mileage, recordedAt);
        return existing;
    }

    /**
     * 安排下次保养。基于上次保养里程 + 设定间隔（直接覆盖 nextMaintenanceMileage）。
     */
    @Transactional
    public AstVehicle scheduleMaintenance(Long id, Long nextMileage) {
        AstVehicle existing = getById(id);
        if (nextMileage == null || nextMileage < 0) {
            throw new ServiceException(400, "nextMileage must be non-negative");
        }
        existing.setNextMaintenanceMileage(nextMileage);
        vehicleMapper.updateById(existing);
        log.info("Vehicle maintenance scheduled id={} nextAt={}", id, nextMileage);
        return existing;
    }
}