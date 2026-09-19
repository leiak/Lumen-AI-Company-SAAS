package com.lumen.contract.controller;

import com.lumen.common.core.domain.R;
import com.lumen.contract.dto.MilestoneRequest;
import com.lumen.contract.entity.Fulfillment;
import com.lumen.contract.service.FulfillmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contract/{id}/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    @GetMapping
    public R<List<Fulfillment>> list(@PathVariable Long id) {
        return R.ok(fulfillmentService.findByContract(id));
    }

    @PostMapping("/record")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Fulfillment> record(@PathVariable Long id, @RequestBody @Valid MilestoneRequest req) {
        return R.ok(fulfillmentService.recordMilestone(id, req));
    }

    @PostMapping("/{fid}/complete")
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Fulfillment> complete(@PathVariable Long id, @PathVariable Long fid,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Long evidenceFileId = body == null || body.get("evidenceFileId") == null ? null
            : Long.valueOf(String.valueOf(body.get("evidenceFileId")));
        return R.ok(fulfillmentService.complete(fid, evidenceFileId));
    }
}
