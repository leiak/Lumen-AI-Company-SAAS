package com.lumen.contract.controller;

import com.lumen.common.core.domain.R;
import com.lumen.contract.entity.Attachment;
import com.lumen.contract.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contract/{id}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public R<List<Attachment>> list(@PathVariable Long id) {
        return R.ok(attachmentService.listAttachments(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin','admin','contract_admin')")
    public R<Attachment> attach(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long fileId = body.get("fileId") == null ? null : Long.valueOf(String.valueOf(body.get("fileId")));
        String type = body.get("type") == null ? AttachmentService.TYPE_OTHER : String.valueOf(body.get("type"));
        return R.ok(attachmentService.attach(id, fileId, type));
    }
}
