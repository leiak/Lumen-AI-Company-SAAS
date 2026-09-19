package com.lumen.procurement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.procurement.dto.BiddingEvaluationDto;
import com.lumen.procurement.entity.ProcBidding;
import com.lumen.procurement.entity.ProcBiddingParticipant;
import com.lumen.procurement.service.ProcBiddingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/proc/bidding")
@RequiredArgsConstructor
@Validated
public class BiddingController {

    private final ProcBiddingService biddingService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<IPage<ProcBidding>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String status) {
        return R.ok(biddingService.page(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<ProcBidding> get(@PathVariable Long id) {
        return R.ok(biddingService.getById(id));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcBidding> create(@RequestBody ProcBidding req) {
        return R.ok(biddingService.create(req));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcBidding> publish(@PathVariable Long id) {
        return R.ok(biddingService.publish(id));
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<List<ProcBiddingParticipant>> invite(@PathVariable Long id,
                                                    @RequestBody List<Long> supplierIds) {
        return R.ok(biddingService.inviteSuppliers(id, supplierIds));
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcBiddingParticipant> join(@PathVariable Long id,
                                          @RequestParam @NotNull Long supplierId,
                                          @RequestParam @NotNull @Positive BigDecimal bidAmount) {
        return R.ok(biddingService.joinBidding(id, supplierId, bidAmount));
    }

    @GetMapping("/{id}/evaluate")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<List<BiddingEvaluationDto>> evaluate(@PathVariable Long id) {
        return R.ok(biddingService.evaluate(id));
    }

    @PostMapping("/{id}/award")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcBidding> award(@PathVariable Long id,
                                 @RequestParam @NotNull Long selectedParticipantId) {
        return R.ok(biddingService.award(id, selectedParticipantId));
    }
}