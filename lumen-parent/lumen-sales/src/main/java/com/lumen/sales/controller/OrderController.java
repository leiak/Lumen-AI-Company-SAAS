package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.entity.Order;
import com.lumen.sales.entity.Receivable;
import com.lumen.sales.entity.Shipment;
import com.lumen.sales.service.OrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sal/order")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/list")
    public R<IPage<Order>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) Long customerId) {
        return R.ok(orderService.page(pageNum, pageSize, status, customerId));
    }

    @GetMapping("/{id}")
    public R<Order> get(@PathVariable Long id) {
        return R.ok(orderService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Order> save(@RequestBody Order req) {
        return R.ok(orderService.save(req));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Order> confirm(@PathVariable Long id) {
        return R.ok(orderService.confirm(id));
    }

    @PostMapping("/{id}/mark-shipping")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Shipment> markShipping(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(orderService.markShipping(id,
            body == null ? null : body.get("carrier"),
            body == null ? null : body.get("trackingNo")));
    }

    @PostMapping("/{id}/mark-shipped")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Order> markShipped(@PathVariable Long id) {
        return R.ok(orderService.markShipped(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Receivable> complete(@PathVariable Long id) {
        return R.ok(orderService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Order> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(orderService.cancel(id, body == null ? null : body.get("reason")));
    }
}