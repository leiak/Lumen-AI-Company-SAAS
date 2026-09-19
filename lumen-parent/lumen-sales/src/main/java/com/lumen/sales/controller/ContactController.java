package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.entity.Contact;
import com.lumen.sales.service.ContactService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sal/contact")
@RequiredArgsConstructor
@Validated
public class ContactController {

    private final ContactService contactService;

    @GetMapping("/list")
    public R<IPage<Contact>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                  @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                  @RequestParam(required = false) Long customerId,
                                  @RequestParam(required = false) String name) {
        return R.ok(contactService.page(pageNum, pageSize, customerId, name));
    }

    @GetMapping("/by-customer")
    public R<List<Contact>> byCustomer(@RequestParam Long customerId) {
        return R.ok(contactService.findByCustomer(customerId));
    }

    @GetMapping("/primary")
    public R<Contact> primary(@RequestParam Long customerId) {
        return R.ok(contactService.findPrimary(customerId));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Contact> save(@RequestBody Contact req) {
        return R.ok(contactService.save(req));
    }

    @PostMapping("/{id}/set-primary")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Contact> setPrimary(@PathVariable Long id) {
        return R.ok(contactService.setPrimary(id));
    }
}