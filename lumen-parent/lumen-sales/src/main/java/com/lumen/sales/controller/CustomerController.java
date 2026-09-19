package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.dto.SaveCustomerRequest;
import com.lumen.sales.entity.Customer;
import com.lumen.sales.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sal/customer")
@RequiredArgsConstructor
@Validated
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/list")
    public R<IPage<Customer>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                   @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) Long ownerUserId) {
        return R.ok(customerService.page(pageNum, pageSize, name, status, ownerUserId));
    }

    @GetMapping("/{id}")
    public R<Customer> get(@PathVariable Long id) {
        return R.ok(customerService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin')")
    public R<Customer> save(@RequestBody @Valid SaveCustomerRequest req) {
        Customer c = new Customer();
        c.setCode(req.getCode());
        c.setName(req.getName());
        c.setLevel(req.getLevel());
        c.setSource(req.getSource());
        c.setIndustry(req.getIndustry());
        c.setScale(req.getScale());
        c.setAddress(req.getAddress());
        return R.ok(customerService.save(c));
    }

    @PostMapping("/{id}/level")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin')")
    public R<Customer> updateLevel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(customerService.updateLevel(id, body == null ? null : body.get("level")));
    }

    /** 从公海抢客户 — sales / sales_admin / admin 均可。 */
    @PostMapping("/{id}/claim")
    public R<Customer> claim(@PathVariable Long id) {
        return R.ok(customerService.claimFromPool(id));
    }

    @PostMapping("/{id}/return")
    public R<Customer> returnToPool(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, String> body) {
        return R.ok(customerService.returnToPool(id, body == null ? null : body.get("reason")));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin')")
    public R<Customer> assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return R.ok(customerService.assign(id, body == null ? null : body.get("toUserId")));
    }

    @GetMapping("/pool")
    public R<List<Customer>> pool(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return R.ok(customerService.findInPool(limit));
    }
}