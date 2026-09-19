package com.lumen.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.Attachment;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.mapper.AttachmentMapper;
import com.lumen.contract.mapper.ContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同附件服务。
 *
 * <p>attach 会校验 file_id 的租户归属 — TODO P5:调 file-service 验证文件确实存在并属于本租户。
 * 当前 lumen-contract 没有跨服务 db 视图,本地先校验调用方的 tenant_id 与 contract 的 tenant_id 一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    public static final String TYPE_MAIN_CONTRACT = "main_contract";
    public static final String TYPE_SUPPLEMENTARY = "supplementary";
    public static final String TYPE_INVOICE = "invoice";
    public static final String TYPE_OTHER = "other";

    private final AttachmentMapper attachmentMapper;
    private final ContractMapper contractMapper;

    @Transactional
    public Attachment attach(Long contractId, Long fileId, String type) {
        UserContext ctx = requireContext();
        Contract c = contractMapper.selectById(contractId);
        if (c == null) throw new ServiceException(404, "Contract not found: " + contractId);
        // Tenant check on the parent contract — already enforced by contractMapper interceptor,
        // but we add a defensive assert in case interceptor is bypassed.
        if (!ctx.getTenantId().equals(c.getTenantId())) {
            throw new ServiceException(404, "Contract not found: " + contractId);
        }
        if (fileId == null) throw new ServiceException(400, "fileId is required");
        if (type == null || type.isBlank()) type = TYPE_OTHER;
        // TODO cross-service: call file-service to verify fileId exists & belongs to this tenant.
        // For P3 we record the attachment and trust the caller.
        Attachment a = new Attachment();
        a.setContractId(contractId);
        a.setFileId(fileId);
        a.setAttachmentType(type);
        a.setUploadedAt(LocalDateTime.now());
        a.setTenantId(ctx.getTenantId());
        attachmentMapper.insert(a);
        log.info("Attached file={} to contract={} type={}", fileId, contractId, type);
        return a;
    }

    public List<Attachment> listAttachments(Long contractId) {
        requireTenant();
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
            .eq(Attachment::getContractId, contractId)
            .orderByDesc(Attachment::getId));
    }

    private UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }

    private void requireTenant() {
        if (UserContextHolder.get() == null || UserContextHolder.get().getTenantId() == null) {
            throw new ServiceException(401, "No tenant context");
        }
    }
}
