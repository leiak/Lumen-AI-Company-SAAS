package com.lumen.sales.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.sales.dto.QuotationItemDto;
import com.lumen.sales.entity.Order;
import com.lumen.sales.entity.Quotation;
import com.lumen.sales.entity.QuotationItem;
import com.lumen.sales.service.QuotationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sal/quotation")
@RequiredArgsConstructor
@Validated
public class QuotationController {

    private final QuotationService quotationService;

    @GetMapping("/list")
    public R<IPage<Quotation>> list(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Long opportunityId) {
        return R.ok(quotationService.page(pageNum, pageSize, status, opportunityId));
    }

    @GetMapping("/{id}")
    public R<Quotation> get(@PathVariable Long id) {
        return R.ok(quotationService.get(id));
    }

    @GetMapping("/{id}/items")
    public R<List<QuotationItem>> items(@PathVariable Long id) {
        return R.ok(quotationService.findItems(id));
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Quotation> save(@RequestBody Map<String, Object> body) {
        Long opportunityId = toLong(body.get("opportunityId"));
        LocalDate validUntil = body.get("validUntil") == null ? null : LocalDate.parse(body.get("validUntil").toString());
        String terms = body.get("terms") == null ? null : body.get("terms").toString();
        List<QuotationItemDto> items = castItems(body.get("items"));
        return R.ok(quotationService.create(opportunityId, items, validUntil, terms));
    }

    @PostMapping("/{id}/create-version")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Quotation> createVersion(@PathVariable Long id) {
        // /sal/quotation/{id}/create-version — id 为 opportunityId (kept for URL compatibility)
        return R.ok(quotationService.createVersion(id));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Quotation> send(@PathVariable Long id) {
        return R.ok(quotationService.send(id));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Order> accept(@PathVariable Long id) {
        return R.ok(quotationService.accept(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('super_admin','admin','sales_admin','sales')")
    public R<Quotation> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(quotationService.reject(id, body == null ? null : body.get("reason")));
    }

    @SuppressWarnings("unchecked")
    private List<QuotationItemDto> castItems(Object obj) {
        if (obj == null) return List.of();
        if (!(obj instanceof List)) {
            throw new IllegalArgumentException("items must be a list");
        }
        List<QuotationItemDto> out = new java.util.ArrayList<>();
        for (Object o : (List<Object>) obj) {
            if (!(o instanceof Map)) throw new IllegalArgumentException("each item must be a map");
            Map<String, Object> m = (Map<String, Object>) o;
            QuotationItemDto d = new QuotationItemDto();
            d.setItemName((String) m.get("itemName"));
            d.setSku((String) m.get("sku"));
            d.setQuantity(m.get("quantity") == null ? null : Integer.valueOf(m.get("quantity").toString()));
            d.setUnitPrice(m.get("unitPrice") == null ? null : new java.math.BigDecimal(m.get("unitPrice").toString()));
            out.add(d);
        }
        return out;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
    }
}