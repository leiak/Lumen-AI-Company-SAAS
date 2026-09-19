package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Shipment;
import com.lumen.sales.mapper.ShipmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 发货单服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_TRANSIT = "in_transit";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_EXCEPTION = "exception";

    private final ShipmentMapper shipmentMapper;
    private final CustomerService customerService;

    public IPage<Shipment> page(int pageNum, int pageSize, String status, Long orderId) {
        var w = new LambdaQueryWrapper<Shipment>().orderByDesc(Shipment::getId);
        if (status != null && !status.isBlank()) w.eq(Shipment::getStatus, status);
        if (orderId != null) w.eq(Shipment::getOrderId, orderId);
        return shipmentMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Shipment get(Long id) {
        Shipment s = shipmentMapper.selectById(id);
        if (s == null) throw new ServiceException(404, "Shipment not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(s.getTenantId())) {
                throw new ServiceException(404, "Shipment not found: " + id);
            }
        }
        return s;
    }

    @Transactional
    public Shipment save(Shipment req) {
        UserContext ctx = requireContext();
        Shipment s = new Shipment();
        s.setCode("S-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        s.setOrderId(req.getOrderId());
        s.setShipmentDate(req.getShipmentDate() == null ? LocalDate.now() : req.getShipmentDate());
        s.setCarrier(req.getCarrier());
        s.setTrackingNo(req.getTrackingNo());
        s.setStatus(req.getStatus() == null ? STATUS_PENDING : req.getStatus());
        s.setTenantId(ctx.getTenantId());
        try {
            shipmentMapper.insert(s);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Shipment code conflict", ex);
        }
        return s;
    }

    @Transactional
    public Shipment markDelivered(Long id) {
        Shipment s = get(id);
        if (STATUS_DELIVERED.equals(s.getStatus())) return s;
        if (STATUS_EXCEPTION.equals(s.getStatus())) {
            throw new ServiceException(409, "Exception shipment cannot be marked delivered");
        }
        s.setStatus(STATUS_DELIVERED);
        shipmentMapper.updateById(s);
        return s;
    }

    @Transactional
    public Shipment markException(Long id, String reason) {
        Shipment s = get(id);
        if (STATUS_DELIVERED.equals(s.getStatus())) {
            throw new ServiceException(409, "Delivered shipment cannot be marked exception");
        }
        s.setStatus(STATUS_EXCEPTION);
        if (reason != null) s.setTrackingNo(s.getTrackingNo()); // reason 在 audit 中记录(P5)
        shipmentMapper.updateById(s);
        log.warn("Shipment {} marked exception: {}", id, reason);
        return s;
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }
}