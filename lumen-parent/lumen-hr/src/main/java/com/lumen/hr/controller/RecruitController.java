package com.lumen.hr.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.hr.dto.AddCandidateRequest;
import com.lumen.hr.dto.CreateJobRequest;
import com.lumen.hr.dto.SendOfferRequest;
import com.lumen.hr.entity.HrOffer;
import com.lumen.hr.entity.HrRecruitCandidate;
import com.lumen.hr.entity.HrRecruitJob;
import com.lumen.hr.service.RecruitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hr/recruit")
@RequiredArgsConstructor
@Validated
public class RecruitController {

    private final RecruitService recruitService;

    @GetMapping("/job/list")
    @PreAuthorize("isAuthenticated()")
    public R<IPage<HrRecruitJob>> listJobs(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                           @RequestParam(required = false) String keyword) {
        return R.ok(recruitService.listJobs(pageNum, pageSize, keyword));
    }

    @PostMapping("/job/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrRecruitJob> createJob(@RequestBody @Valid CreateJobRequest req) {
        return R.ok(recruitService.createJob(req));
    }

    @PostMapping("/job/{id}/publish")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrRecruitJob> publishJob(@PathVariable Long id) {
        return R.ok(recruitService.publishJob(id));
    }

    @PostMapping("/candidate/add")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrRecruitCandidate> addCandidate(@RequestBody @Valid AddCandidateRequest req) {
        return R.ok(recruitService.addCandidate(req));
    }

    @PostMapping("/candidate/{id}/stage")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrRecruitCandidate> advanceStage(@PathVariable Long id, @RequestParam @Min(0) @Max(6) int stage) {
        return R.ok(recruitService.advanceStage(id, stage));
    }

    @PostMapping("/offer/send")
    @PreAuthorize("hasAnyRole('super_admin','admin','hr_admin')")
    public R<HrOffer> sendOffer(@RequestBody @Valid SendOfferRequest req) {
        return R.ok(recruitService.sendOffer(req));
    }
}
