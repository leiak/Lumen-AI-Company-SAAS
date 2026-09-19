package com.lumen.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.entity.SignTask;
import com.lumen.contract.mapper.ContractMapper;
import com.lumen.contract.mapper.SignTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合同主流程服务。状态机:
 * <pre>
 *   drafting -> pending_approval -> approved -> signing -> signed -> fulfilling -> expired/terminated/archived
 * </pre>
 * 单向迁移。任何写方法必须经 assertStatusTransition 校验,违规 → 409。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    // 状态常量 (与 DB ENUM 字符串值一一对应)
    public static final String STATUS_DRAFTING = "drafting";
    public static final String STATUS_PENDING_APPROVAL = "pending_approval";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_SIGNING = "signing";
    public static final String STATUS_SIGNED = "signed";
    public static final String STATUS_FULFILLING = "fulfilling";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_TERMINATED = "terminated";
    public static final String STATUS_ARCHIVED = "archived";

    private static final Set<String> ALLOWED_STATUSES = Set.of(
        STATUS_DRAFTING, STATUS_PENDING_APPROVAL, STATUS_APPROVED,
        STATUS_SIGNING, STATUS_SIGNED, STATUS_FULFILLING,
        STATUS_EXPIRED, STATUS_TERMINATED, STATUS_ARCHIVED);

    /** Legal single-direction transitions. Archive is ONLY allowed from signed/fulfilling/expired/terminated. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        STATUS_DRAFTING, Set.of(STATUS_PENDING_APPROVAL, STATUS_TERMINATED),
        STATUS_PENDING_APPROVAL, Set.of(STATUS_APPROVED, STATUS_DRAFTING, STATUS_TERMINATED),
        STATUS_APPROVED, Set.of(STATUS_SIGNING, STATUS_TERMINATED),
        STATUS_SIGNING, Set.of(STATUS_SIGNED, STATUS_TERMINATED),
        STATUS_SIGNED, Set.of(STATUS_FULFILLING, STATUS_TERMINATED, STATUS_ARCHIVED),
        STATUS_FULFILLING, Set.of(STATUS_EXPIRED, STATUS_TERMINATED, STATUS_ARCHIVED),
        STATUS_EXPIRED, Set.of(STATUS_ARCHIVED),
        STATUS_TERMINATED, Set.of(STATUS_ARCHIVED),
        STATUS_ARCHIVED, Set.of());

    private final ContractMapper contractMapper;
    private final SignTaskMapper signTaskMapper;
    private final ChangeLogService changeLogService;
    private final TemplateService templateService;
    private final ClauseService clauseService;

    /** Status constant accessor (used by tests). */
    public static Set<String> allowedStatuses() {
        return ALLOWED_STATUSES;
    }

    // ---------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------

    public IPage<Contract> page(int pageNum, int pageSize, String status, String type, Long drafterId) {
        var w = new LambdaQueryWrapper<Contract>().orderByDesc(Contract::getId);
        if (status != null && !status.isBlank()) w.eq(Contract::getStatus, status);
        if (type != null && !type.isBlank()) w.eq(Contract::getType, type);
        if (drafterId != null) w.eq(Contract::getDrafterId, drafterId);
        return contractMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    /**
     * Get a contract by id — enforces tenant scoping. Cross-tenant returns 404
     * (not 403) to avoid existence disclosure.
     */
    public Contract get(Long id) {
        Contract c = contractMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Contract not found: " + id);
        UserContext ctx = requireContext();
        Set<String> roles = ctx.getRoles();
        boolean isSuperAdmin = roles != null && roles.contains("super_admin");
        if (!isSuperAdmin) {
            Long ctxTenant = ctx.getTenantId();
            if (ctxTenant == null || !ctxTenant.equals(c.getTenantId())) {
                throw new ServiceException(404, "Contract not found: " + id);
            }
        }
        return c;
    }

    // ---------------------------------------------------------------
    // Status transition guard
    // ---------------------------------------------------------------

    /**
     * Validates a status transition. Throws 409 on illegal transition.
     * Bypassed for terminal {@code archived} (the graph allows self-loop = noop).
     */
    public void assertStatusTransition(String from, String to) {
        if (from == null || to == null) {
            throw new ServiceException(400, "Status from/to required");
        }
        if (!ALLOWED_STATUSES.contains(from)) {
            throw new ServiceException(409, "Unknown current status: " + from);
        }
        if (!ALLOWED_STATUSES.contains(to)) {
            throw new ServiceException(400, "Unknown target status: " + to);
        }
        Set<String> next = TRANSITIONS.getOrDefault(from, Set.of());
        if (!next.contains(to)) {
            throw new ServiceException(409,
                "Illegal status transition: " + from + " -> " + to);
        }
    }

    /** Asserts the current user is allowed to mutate a contract in drafting state. */
    public void assertDrafterOrAdmin(Contract c) {
        UserContext ctx = requireContext();
        Set<String> roles = ctx.getRoles();
        boolean isAdmin = roles != null && (roles.contains("super_admin")
            || roles.contains("admin") || roles.contains("contract_admin"));
        Long uid = ctx.getUserId();
        if (!isAdmin && (uid == null || !uid.equals(c.getDrafterId()))) {
            throw new ServiceException(403,
                "Only drafter or admin can mutate drafting contract");
        }
    }

    // ---------------------------------------------------------------
    // Write
    // ---------------------------------------------------------------

    @Transactional
    public Contract save(Contract req) {
        UserContext ctx = requireContext();
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ServiceException(400, "title is required");
        }
        if (req.getContractNo() == null || req.getContractNo().isBlank()) {
            throw new ServiceException(400, "contractNo is required");
        }
        Contract toCreate = new Contract();
        toCreate.setContractNo(req.getContractNo());
        toCreate.setTitle(req.getTitle());
        toCreate.setType(req.getType());
        toCreate.setPartyA(req.getPartyA());
        toCreate.setPartyB(req.getPartyB());
        toCreate.setAmount(req.getAmount());
        toCreate.setCurrency(req.getCurrency());
        toCreate.setStartDate(req.getStartDate());
        toCreate.setEndDate(req.getEndDate());
        toCreate.setTemplateId(req.getTemplateId());
        toCreate.setDrafterId(ctx.getUserId());
        toCreate.setStatus(STATUS_DRAFTING);
        toCreate.setTenantId(ctx.getTenantId());
        try {
            contractMapper.insert(toCreate);
        } catch (DuplicateKeyException ex) {
            // UNIQUE (tenant_id, contract_no, deleted) — surface as 409.
            throw new ServiceException(409, "contractNo already exists: " + req.getContractNo(), ex);
        }
        changeLogService.record(toCreate.getId(), "draft", null,
            Map.of("title", toCreate.getTitle(), "contractNo", toCreate.getContractNo()),
            "created");
        log.info("Created contract id={} no={}", toCreate.getId(), toCreate.getContractNo());
        return toCreate;
    }

    /**
     * Draft from template — validates required template variables, creates contract in drafting,
     * then auto-generates a starter clause for the template (the caller's
     * ClauseService.bulkSave can replace this when actual clauses arrive).
     */
    @Transactional
    public Contract draft(Long templateId, Map<String, Object> variables) {
        UserContext ctx = requireContext();
        TemplateService.Rendered rendered = templateService.renderVariables(templateId, variables);
        Contract c = new Contract();
        c.setTemplateId(templateId);
        c.setTitle(rendered.name() == null ? "draft-" + templateId : rendered.name());
        c.setType(rendered.type());
        c.setDrafterId(ctx.getUserId());
        c.setStatus(STATUS_DRAFTING);
        c.setTenantId(ctx.getTenantId());
        contractMapper.insert(c);
        // Auto-create a starter clause carrying the rendered variable snapshot.
        clauseService.autoSeedFromTemplate(c.getId(), rendered);
        changeLogService.record(c.getId(), "draft", null,
            Map.of("templateId", templateId, "variables", rendered.variables()),
            "drafted from template");
        log.info("Drafted contract id={} templateId={}", c.getId(), templateId);
        return c;
    }

    /**
     * Submit for approval — drafting -> pending_approval. After P4 wires workflow
     * this will call {@code engineService.startInstance}. For P3 it's a status flip only.
     */
    @Transactional
    public Contract submit(Long id, List<Long> signerUserIds, List<String> roles) {
        Contract c = get(id);
        if (signerUserIds == null || signerUserIds.isEmpty()) {
            throw new ServiceException(400, "signerUserIds must not be empty");
        }
        if (roles == null || roles.size() != signerUserIds.size()) {
            throw new ServiceException(400, "roles must align with signerUserIds");
        }
        assertDrafterOrAdmin(c);
        assertStatusTransition(c.getStatus(), STATUS_PENDING_APPROVAL);
        // TODO workflow.startInstance("contract-approval", businessKey=c.getId(), variables)
        Map<String, Object> before = new HashMap<>();
        before.put("status", c.getStatus());
        c.setStatus(STATUS_PENDING_APPROVAL);
        contractMapper.updateById(c);
        changeLogService.record(c.getId(), "draft", before,
            Map.of("status", STATUS_PENDING_APPROVAL, "signerUserIds", signerUserIds),
            "submitted for approval");
        return c;
    }

    /** Workflow callback — pending_approval -> approved. */
    @Transactional
    public Contract approve(Long id, String comment) {
        Contract c = get(id);
        assertStatusTransition(c.getStatus(), STATUS_APPROVED);
        Map<String, Object> before = new HashMap<>();
        before.put("status", c.getStatus());
        c.setStatus(STATUS_APPROVED);
        contractMapper.updateById(c);
        changeLogService.record(c.getId(), "approve", before,
            Map.of("status", STATUS_APPROVED),
            comment == null ? "approved" : comment);
        return c;
    }

    /** Manually transition to signing (called by SignService when first task is dispatched). */
    @Transactional
    public void markSigning(Long id) {
        Contract c = get(id);
        if (STATUS_SIGNING.equals(c.getStatus())) return;
        assertStatusTransition(c.getStatus(), STATUS_SIGNING);
        Map<String, Object> before = Map.of("status", c.getStatus());
        c.setStatus(STATUS_SIGNING);
        contractMapper.updateById(c);
        changeLogService.record(c.getId(), "sign", before,
            Map.of("status", STATUS_SIGNING),
            "signing started");
    }

    /**
     * SignService.checkStatus flips to {@code signed} once all tasks reach status=signed.
     */
    @Transactional
    public void markSigned(Long id) {
        Contract c = get(id);
        assertStatusTransition(c.getStatus(), STATUS_SIGNED);
        Map<String, Object> before = Map.of("status", c.getStatus());
        c.setStatus(STATUS_SIGNED);
        contractMapper.updateById(c);
        changeLogService.record(c.getId(), "sign", before,
            Map.of("status", STATUS_SIGNED),
            "all signers signed");
    }

    /** Promote to fulfilling — used by FulfillmentService when first milestone recorded. */
    @Transactional
    public void markFulfilling(Long id) {
        Contract c = get(id);
        if (STATUS_FULFILLING.equals(c.getStatus())) return;
        assertStatusTransition(c.getStatus(), STATUS_FULFILLING);
        Map<String, Object> before = Map.of("status", c.getStatus());
        c.setStatus(STATUS_FULFILLING);
        contractMapper.updateById(c);
        changeLogService.record(c.getId(), "other", before,
            Map.of("status", STATUS_FULFILLING),
            "fulfillment started");
    }

    /** Terminate — writes reason to change_log. */
    @Transactional
    public Contract terminate(Long id, String reason) {
        Contract c = get(id);
        assertStatusTransition(c.getStatus(), STATUS_TERMINATED);
        Map<String, Object> before = Map.of("status", c.getStatus());
        c.setStatus(STATUS_TERMINATED);
        contractMapper.updateById(c);
        changeLogService.record(c.getId(), "terminate", before,
            Map.of("status", STATUS_TERMINATED),
            reason == null ? "terminated" : reason);
        return c;
    }

    /** Archive — only from {@code signed/fulfilling/expired/terminated}. */
    @Transactional
    public Contract archive(Long id) {
        Contract c = get(id);
        assertStatusTransition(c.getStatus(), STATUS_ARCHIVED);
        Map<String, Object> before = Map.of("status", c.getStatus());
        c.setStatus(STATUS_ARCHIVED);
        contractMapper.updateById(c);
        changeLogService.record(c.getId(), "other", before,
            Map.of("status", STATUS_ARCHIVED),
            "archived");
        return c;
    }

    private UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }

    /** Convenience used by SignService to fetch sign-tasks for a contract (avoids service-cycle). */
    public List<SignTask> signTasks(Long contractId) {
        return signTaskMapper.findByContract(contractId, requireContext().getTenantId());
    }
}
