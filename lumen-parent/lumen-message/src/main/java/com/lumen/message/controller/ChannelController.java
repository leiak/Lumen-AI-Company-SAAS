package com.lumen.message.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.message.entity.MsgChannel;
import com.lumen.message.service.ChannelService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/message/channel")
@RequiredArgsConstructor
@Validated
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<IPage<MsgChannel>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                     @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                     @RequestParam(required = false) String keyword) {
        return R.ok(channelService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<MsgChannel> get(@PathVariable Long id) {
        return R.ok(channelService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<MsgChannel> create(@RequestBody MsgChannel req) {
        return R.ok(channelService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<MsgChannel> update(@PathVariable Long id, @RequestBody MsgChannel req) {
        req.setId(id);
        return R.ok(channelService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin','admin')")
    public R<Void> delete(@PathVariable Long id) {
        channelService.delete(id);
        return R.ok();
    }
}