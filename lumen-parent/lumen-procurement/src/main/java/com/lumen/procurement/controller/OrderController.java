package com.lumen.procurement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.procurement.dto.CreateOrderRequest;
import com.lumen.procurement.entity.ProcOrder;
import com.lumen.procurement.entity.ProcOrderItem;
import com.lumen.procurement.service.ProcOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proc/order")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final ProcOrderService orderService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<IPage<ProcOrder>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status) {
        return R.ok(orderService.page(pageNum, pageSize, supplierId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<ProcOrder> get(@PathVariable Long id) {
        return R.ok(orderService.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<List<ProcOrderItem>> items(@PathVariable Long id) {
        return R.ok(orderService.listItems(id));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcOrder> create(@RequestBody @Valid CreateOrderRequest req) {
        return R.ok(orderService.create(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcOrder> submit(@PathVariable Long id) {
        return R.ok(orderService.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcOrder> approve(@PathVariable Long id) {
        return R.ok(orderService.approve(id, null));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcOrder> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(orderService.reject(id, reason));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcOrder> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(orderService.cancel(id, reason));
    }

    @PostMapping("/{id}/mark-fulfilled")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcOrder> markFulfilled(@PathVariable Long id) {
        return R.ok(orderService.markFulfilled(id));
    }
}