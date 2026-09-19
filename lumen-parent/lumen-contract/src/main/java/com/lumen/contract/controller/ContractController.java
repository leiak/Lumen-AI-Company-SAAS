package com.lumen.contract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.contract.dto.DraftContractRequest;
import com.lumen.contract.dto.SubmitContractRequest;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.service.ContractService;
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
@RequestMapping("/contract")
@RequiredArgsConstructor
@Validated
public class ContractController {

    private final ContractService contractService;

    @GetMapping("/list")
    public R<IPage<Contract>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) Long drafterId) {
        return R.ok(contractService.page(pageNum, pageSize, status, type, drafterId));
    }

    @GetMapping("/{id}")
    public R<Contract> get(@PathVariable Long id) {
        return R.ok(contractService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Contract> save(@RequestBody @Valid Contract req) {
        return R.ok(contractService.save(req));
    }

    @PostMapping("/draft")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Contract> draft(@RequestBody @Valid DraftContractRequest req) {
        return R.ok(contractService.draft(req.getTemplateId(), req.getVariables()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Contract> submit(@PathVariable Long id, @RequestBody @Valid SubmitContractRequest req) {
        return R.ok(contractService.submit(id, req.getSignerUserIds(), req.getRoles()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Contract> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null ? null : (String) body.get("comment");
        return R.ok(contractService.approve(id, comment));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Contract> terminate(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (String) body.get("reason");
        return R.ok(contractService.terminate(id, reason));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Contract> archive(@PathVariable Long id) {
        return R.ok(contractService.archive(id));
    }

    /** Find contracts expiring within N days — used by expiration reminder (Quartz TODO). */
    @GetMapping("/expiring")
    public R<List<Contract>> expiring(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        // TODO P5: implement expiring query (compare end_date vs now+days, return list).
        return R.ok(List.of());
    }
}
