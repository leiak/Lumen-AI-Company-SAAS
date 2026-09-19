package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.entity.Lead;
import com.lumen.sales.service.LeadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sal/lead")
@RequiredArgsConstructor
@Validated
public class LeadController {

    private final LeadService leadService;

    @GetMapping("/list")
    public R<IPage<Lead>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                               @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String source,
                               @RequestParam(required = false) Long ownerUserId) {
        return R.ok(leadService.page(pageNum, pageSize, status, source, ownerUserId));
    }

    @GetMapping("/{id}")
    public R<Lead> get(@PathVariable Long id) {
        return R.ok(leadService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Lead> save(@RequestBody Lead req) {
        return R.ok(leadService.save(req));
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Customer> convert(@PathVariable Long id) {
        return R.ok(leadService.convertToCustomer(id));
    }
}