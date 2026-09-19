package com.lumen.procurement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.procurement.dto.AwardInquiryRequest;
import com.lumen.procurement.dto.CreateInquiryRequest;
import com.lumen.procurement.dto.QuotationDto;
import com.lumen.procurement.dto.QuotationSummaryDto;
import com.lumen.procurement.entity.ProcInquiry;
import com.lumen.procurement.entity.ProcOrder;
import com.lumen.procurement.entity.ProcQuotation;
import com.lumen.procurement.service.ProcInquiryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/proc/inquiry")
@RequiredArgsConstructor
@Validated
public class InquiryController {

    private final ProcInquiryService inquiryService;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<IPage<ProcInquiry>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
            @RequestParam(required = false) String status) {
        return R.ok(inquiryService.page(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<ProcInquiry> get(@PathVariable Long id) {
        return R.ok(inquiryService.getById(id));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcInquiry> create(@RequestBody @Valid CreateInquiryRequest req) {
        return R.ok(inquiryService.create(req));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcInquiry> publish(@PathVariable Long id, @RequestBody List<Long> supplierIds) {
        return R.ok(inquiryService.publish(id, supplierIds));
    }

    @PostMapping("/{id}/quote")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcQuotation> quote(@PathVariable Long id, @RequestBody @Valid QuotationDto dto) {
        return R.ok(inquiryService.submitQuotation(id, dto));
    }

    @GetMapping("/{id}/compare-quote")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin','user')")
    public R<List<QuotationSummaryDto>> compareQuote(@PathVariable Long id) {
        return R.ok(inquiryService.compareQuote(id));
    }

    @PostMapping("/{id}/award")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcInquiry> award(@PathVariable Long id, @RequestBody @Valid AwardInquiryRequest req) {
        return R.ok(inquiryService.award(id, req));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcInquiry> close(@PathVariable Long id) {
        return R.ok(inquiryService.close(id));
    }

    /**
     * 从中标的 quotation 直接创建采购单。
     */
    @PostMapping("/quotation/{quotationId}/create-order")
    @PreAuthorize("hasAnyRole('super_admin','admin','procurement_admin')")
    public R<ProcOrder> createOrderFromQuotation(
            @PathVariable Long quotationId,
            @RequestParam String orderCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDeliveryAt) {
        return R.ok(inquiryService.createOrderFromQuotation(quotationId, orderCode, orderDate, expectedDeliveryAt));
    }
}