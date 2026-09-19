package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.entity.Shipment;
import com.lumen.sales.service.ShipmentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sal/shipment")
@RequiredArgsConstructor
@Validated
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping("/list")
    public R<IPage<Shipment>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                  @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Long orderId) {
        return R.ok(shipmentService.page(pageNum, pageSize, status, orderId));
    }

    @GetMapping("/{id}")
    public R<Shipment> get(@PathVariable Long id) {
        return R.ok(shipmentService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Shipment> save(@RequestBody Shipment req) {
        return R.ok(shipmentService.save(req));
    }

    @PostMapping("/{id}/mark-delivered")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Shipment> markDelivered(@PathVariable Long id) {
        return R.ok(shipmentService.markDelivered(id));
    }

    @PostMapping("/{id}/mark-exception")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Shipment> markException(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(shipmentService.markException(id, body == null ? null : body.get("reason")));
    }
}