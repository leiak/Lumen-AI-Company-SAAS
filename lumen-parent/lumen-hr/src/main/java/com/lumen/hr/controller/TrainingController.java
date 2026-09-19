package com.lumen.hr.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.hr.dto.CompleteTrainingRequest;
import com.lumen.hr.dto.CreateTrainingPlanRequest;
import com.lumen.hr.entity.HrTrainingPlan;
import com.lumen.hr.entity.HrTrainingRecord;
import com.lumen.hr.service.TrainingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hr/training")
@RequiredArgsConstructor
@Validated
public class TrainingController {

    private final TrainingService trainingService;

    @GetMapping("/plan/list")
    @PreAuthorize("isAuthenticated()")
    public R<IPage<HrTrainingPlan>> listPlans(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                              @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                              @RequestParam(required = false) String keyword) {
        return R.ok(trainingService.listPlans(pageNum, pageSize, keyword));
    }

    @PostMapping("/plan/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrTrainingPlan> createPlan(@RequestBody @Valid CreateTrainingPlanRequest req) {
        return R.ok(trainingService.createPlan(req));
    }

    @PostMapping("/enroll")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrTrainingRecord> enroll(@RequestParam Long planId, @RequestParam Long employeeId) {
        return R.ok(trainingService.enroll(planId, employeeId));
    }

    @PostMapping("/record/complete")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrTrainingRecord> complete(@RequestBody @Valid CompleteTrainingRequest req) {
        return R.ok(trainingService.complete(req));
    }

    @GetMapping("/record/by-employee")
    @PreAuthorize("isAuthenticated()")
    public R<List<HrTrainingRecord>> byEmployee(@RequestParam Long employeeId) {
        return R.ok(trainingService.listByEmployee(employeeId));
    }
}
