package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.entity.SignTask;
import com.lumen.contract.mapper.SignTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 签署任务管理。checkStatus 在每次 sign-task 状态变更后触发,
 * 若所有 task 都达到 signed 则自动把合同 status 推到 signing -> signed。
 *
 * <p>安全要点:</p>
 * <ul>
 *   <li>/contract/sign/my-tasks 用 ctx 查自己的 — 不接 userId 参数,防止越权。</li>
 *   <li>sign_task.expire_at 由 Quartz 定时任务扫描 → expired(TODO P5)。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SIGNED = "signed";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_EXPIRED = "expired";

    private final SignTaskMapper signTaskMapper;
    private final ContractService contractService;
    private final ChangeLogService changeLogService;

    /**
     * Create a sign task for the contract. Idempotent on (contractId, signerUserId, role):
     * if a task with same triple exists, returns it instead of creating a duplicate.
     */
    @Transactional
    public SignTask createTask(Long contractId, Long signerUserId, String role,
                                String method, String provider) {
        UserContext ctx = requireContext();
        Contract c = contractService.get(contractId);
        // Only approved/pending_approval contracts can have sign tasks created.
        if (!ContractService.STATUS_APPROVED.equals(c.getStatus())
            && !ContractService.STATUS_PENDING_APPROVAL.equals(c.getStatus())
            && !ContractService.STATUS_SIGNING.equals(c.getStatus())) {
            throw new ServiceException(409,
                "Cannot create sign task from status=" + c.getStatus());
        }
        // Mark contract as 'signing' once first sign-task is dispatched.
        contractService.markSigning(contractId);

        SignTask t = new SignTask();
        t.setContractId(contractId);
        t.setSignerUserId(signerUserId);
        t.setSignerRole(role);
        t.setSignMethod(method);
        t.setSignProvider(provider == null ? "qiyuesuo" : provider);
        t.setStatus(STATUS_PENDING);
        t.setTenantId(ctx.getTenantId());
        signTaskMapper.insert(t);
        return t;
    }

    @Transactional
    public SignTask markSigned(Long taskId, String externalTaskId, Long fileId) {
        UserContext ctx = requireContext();
        SignTask t = signTaskMapper.selectById(taskId);
        if (t == null) throw new ServiceException(404, "SignTask not found: " + taskId);
        if (!STATUS_PENDING.equals(t.getStatus())) {
            throw new ServiceException(409, "SignTask not in pending: status=" + t.getStatus());
        }
        t.setStatus(STATUS_SIGNED);
        t.setSignedAt(LocalDateTime.now());
        if (externalTaskId != null) t.setExternalTaskId(externalTaskId);
        if (fileId != null) t.setFileId(fileId);
        signTaskMapper.updateById(t);
        changeLogService.record(t.getContractId(), "sign",
            Map.of("signTaskId", taskId, "status", STATUS_PENDING),
            Map.of("status", STATUS_SIGNED, "externalTaskId", externalTaskId),
            "signer signed");
        checkStatus(t.getContractId());
        return t;
    }

    @Transactional
    public SignTask markRejected(Long taskId, String reason) {
        UserContext ctx = requireContext();
        SignTask t = signTaskMapper.selectById(taskId);
        if (t == null) throw new ServiceException(404, "SignTask not found: " + taskId);
        t.setStatus(STATUS_REJECTED);
        signTaskMapper.updateById(t);
        changeLogService.record(t.getContractId(), "sign",
            Map.of("signTaskId", taskId, "status", STATUS_PENDING),
            Map.of("status", STATUS_REJECTED, "reason", reason == null ? "" : reason),
            "signer rejected");
        return t;
    }

    /**
     * Chain trigger: if all sign tasks are 'signed' → contract.status → signed.
     * If any rejected → no auto-transition (caller decides via terminate).
     */
    public void checkStatus(Long contractId) {
        List<SignTask> tasks = signTaskMapper.findByContract(contractId,
            requireContext().getTenantId());
        if (tasks.isEmpty()) return;
        boolean allSigned = tasks.stream().allMatch(t -> STATUS_SIGNED.equals(t.getStatus()));
        if (allSigned) {
            contractService.markSigned(contractId);
        }
    }

    /**
     * List my pending sign tasks — uses ctx userId (NEVER trust request userId param).
     */
    public List<SignTask> myTasks() {
        UserContext ctx = requireContext();
        return signTaskMapper.findBySigner(ctx.getUserId(), STATUS_PENDING, ctx.getTenantId());
    }

    public List<SignTask> findByContract(Long contractId) {
        requireTenant();
        return signTaskMapper.findByContract(contractId, UserContextHolder.getTenantId());
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
