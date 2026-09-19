package com.lumen.message.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.message.entity.MsgSubscription;
import com.lumen.message.service.SubscriptionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/message/subscription")
@RequiredArgsConstructor
@Validated
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/page")
    @PreAuthorize("isAuthenticated()")
    public R<IPage<MsgSubscription>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                          @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize) {
        return R.ok(subscriptionService.page(pageNum, pageSize));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<MsgSubscription> create(@RequestBody MsgSubscription req) {
        return R.ok(subscriptionService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<MsgSubscription> update(@PathVariable Long id, @RequestBody MsgSubscription req) {
        req.setId(id);
        return R.ok(subscriptionService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long id) {
        subscriptionService.delete(id);
        return R.ok();
    }
}