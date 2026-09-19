package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.dto.SaveOpportunityRequest;
import com.lumen.sales.dto.StageUpdateRequest;
import com.lumen.sales.entity.Opportunity;
import com.lumen.sales.service.OpportunityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sal/opportunity")
@RequiredArgsConstructor
@Validated
public class OpportunityController {

    private final OpportunityService opportunityService;

    @GetMapping("/list")
    public R<IPage<Opportunity>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                      @RequestParam(required = false) String stage,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) Long ownerUserId) {
        return R.ok(opportunityService.page(pageNum, pageSize, stage, status, ownerUserId));
    }

    @GetMapping("/{id}")
    public R<Opportunity> get(@PathVariable Long id) {
        return R.ok(opportunityService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Opportunity> save(@RequestBody @Valid SaveOpportunityRequest req) {
        Opportunity o = new Opportunity();
        o.setName(req.getName());
        o.setCustomerId(req.getCustomerId());
        o.setLeadId(req.getLeadId());
        o.setAmount(req.getAmount());
        o.setProbability(req.getProbability());
        o.setExpectedCloseDate(req.getExpectedCloseDate());
        return R.ok(opportunityService.save(o));
    }

    @PostMapping("/{id}/stage-update")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Opportunity> stageUpdate(@PathVariable Long id,
                                      @RequestBody @Valid StageUpdateRequest req) {
        return R.ok(opportunityService.stageUpdate(id, req.getNewStage(), req.getExpectedCloseDate()));
    }

    @PostMapping("/{id}/mark-won")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Opportunity> markWon(@PathVariable Long id) {
        return R.ok(opportunityService.markWon(id));
    }

    @PostMapping("/{id}/mark-lost")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Opportunity> markLost(@PathVariable Long id,
                                   @RequestBody(required = false) Map<String, String> body) {
        return R.ok(opportunityService.markLost(id, body == null ? null : body.get("reason")));
    }
}