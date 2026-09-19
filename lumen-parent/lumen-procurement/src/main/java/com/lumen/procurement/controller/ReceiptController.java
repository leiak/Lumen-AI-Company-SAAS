package com.lumen.procurement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.procurement.dto.ConfirmReceiptRequest;
import com.lumen.procurement.entity.ProcReceipt;
import com.lumen.procurement.service.ProcReceiptService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/proc/receipt")
@RequiredArgsConstructor
@Validated
public class ReceiptController {

    private final ProcReceiptService receiptService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<IPage<ProcReceipt>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String status) {
        return R.ok(receiptService.page(pageNum, pageSize, orderId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<ProcReceipt> get(@PathVariable Long id) {
        return R.ok(receiptService.getById(id));
    }

    @PostMapping("/{orderId}/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcReceipt> create(@PathVariable Long orderId,
                                   @RequestParam String code,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receiptDate) {
        return R.ok(receiptService.create(orderId, code, receiptDate));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcReceipt> confirm(@PathVariable Long id, @RequestBody ConfirmReceiptRequest req) {
        return R.ok(receiptService.confirm(id, req));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<List<ProcReceipt>> pending() {
        return R.ok(receiptService.findPending());
    }
}