package com.lumen.hr.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.hr.dto.SaveEmployeeRequest;
import com.lumen.hr.dto.TransferRequest;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrTransfer;
import com.lumen.hr.service.EmployeeService;
import com.lumen.hr.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hr/employee")
@RequiredArgsConstructor
@Validated
public class EmployeeController {

    private final EmployeeService employeeService;
    private final TransferService transferService;

    /** Profile endpoint — the current user can read their own record without admin. */
    @GetMapping("/profile")
    public R<HrEmployee> profile() {
        return R.ok(employeeService.getProfile());
    }

    /** List — admin-only. */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<IPage<HrEmployee>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                     @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Long deptId,
                                     @RequestParam(required = false) Integer status) {
        return R.ok(employeeService.list(pageNum, pageSize, keyword, deptId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<HrEmployee> get(@PathVariable Long id) {
        return R.ok(employeeService.getById(id));
    }

    /** Default detail endpoint masks idCardEnc / mobileEnc — full values are exposed only
     * through the explicit {@code /sensitive-info} endpoint with admin RBAC. */
    @GetMapping("/{id}/masked")
    @PreAuthorize("isAuthenticated()")
    public R<HrEmployee> getMasked(@PathVariable Long id) {
        HrEmployee e = employeeService.getById(id);
        if (e.getIdCardEnc() != null) e.setIdCardEnc("***");
        if (e.getMobileEnc() != null) e.setMobileEnc("***");
        return R.ok(e);
    }

    /** Sensitive plaintext — admin-only. */
    @GetMapping("/{id}/sensitive-info")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrEmployee> sensitive(@PathVariable Long id) {
        return R.ok(employeeService.getById(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrEmployee> save(@RequestBody @Valid SaveEmployeeRequest req) {
        return R.ok(employeeService.save(req));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrEmployee> transfer(@RequestBody @Valid TransferRequest req) {
        return R.ok(employeeService.transfer(
            req.getEmployeeId(), req.getToDeptId(), req.getToPostId(), req.getEffectiveAt()));
    }

    @GetMapping("/transfer/history")
    @PreAuthorize("isAuthenticated()")
    public R<List<HrTransfer>> transferHistory(@RequestParam Long employeeId) {
        return R.ok(transferService.history(employeeId));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<HrEmployee> restore(@PathVariable Long id) {
        return R.ok(employeeService.restore(id));
    }
}
