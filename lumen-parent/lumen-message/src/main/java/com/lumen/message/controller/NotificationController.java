package com.lumen.message.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.message.dto.MarkReadRequest;
import com.lumen.message.dto.SendNotificationRequest;
import com.lumen.message.entity.MsgNotification;
import com.lumen.message.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/message/notification")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Send a notification. The {@code userId} is pinned from the session;
     * the request body MUST NOT contain a {@code userId} field — the
     * dispatcher targets {@code recipientUserIds} only (安全 #4).
     */
    @PostMapping("/send")
    @PreAuthorize("isAuthenticated()")
    public R<List<MsgNotification>> send(@RequestBody @Valid SendNotificationRequest req) {
        List<Long> recipients = req.getRecipientUserIds() == null
            ? Collections.emptyList() : req.getRecipientUserIds();
        return R.ok(notificationService.send(req.getTemplateCode(), recipients, req.getVariables()));
    }

    /**
     * List notifications for the calling user. {@code userId} is forced from
     * session — a query/body param of the same name is forbidden (安全 #4).
     */
    @GetMapping("/page")
    @PreAuthorize("isAuthenticated()")
    public R<IPage<MsgNotification>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNum,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(200) int pageSize,
                                           @RequestParam(required = false) Integer status) {
        Long uid = UserContextHolder.getUserId();
        if (uid == null) throw new ServiceException(401, "No user context");
        return R.ok(notificationService.listByUser(uid, status, pageNum, pageSize));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public R<MsgNotification> markRead(@PathVariable Long id) {
        return R.ok(notificationService.markRead(id));
    }

    @PostMapping("/readBatch")
    @PreAuthorize("isAuthenticated()")
    public R<Integer> markReadBatch(@RequestBody @Valid MarkReadRequest req) {
        return R.ok(notificationService.markReadBatch(req.getIds()));
    }
}