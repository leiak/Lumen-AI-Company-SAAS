package com.lumen.inventory.controller;

import com.lumen.common.core.domain.R;
import com.lumen.inventory.dto.CreateTransferRequest;
import com.lumen.inventory.entity.InvTransfer;
import com.lumen.inventory.entity.InvTransferItem;
import com.lumen.inventory.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inv/transfer")
@RequiredArgsConstructor
@Validated
public class TransferController {

    private final TransferService transferService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<InvTransfer> get(@PathVariable Long id) {
        return R.ok(transferService.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin','user')")
    public R<List<InvTransferItem>> items(@PathVariable Long id) {
        return R.ok(transferService.listItems(id));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvTransfer> create(@RequestBody @Valid CreateTransferRequest req) {
        return R.ok(transferService.create(req));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvTransfer> ship(@PathVariable Long id) {
        return R.ok(transferService.ship(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvTransfer> receive(@PathVariable Long id) {
        return R.ok(transferService.receive(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('super_admin','admin','inventory_admin')")
    public R<InvTransfer> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(transferService.cancel(id, reason));
    }
}