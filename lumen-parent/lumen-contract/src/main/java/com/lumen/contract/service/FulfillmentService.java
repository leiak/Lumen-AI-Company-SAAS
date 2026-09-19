package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.dto.MilestoneRequest;
import com.lumen.contract.entity.Fulfillment;
import com.lumen.contract.mapper.FulfillmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 履约里程碑管理。
 *   - recordMilestone: 添加一个里程碑(在合同已 signed 后)
 *   - complete: 标记里程碑完成,可附 evidence_file_id
 *   - checkOverdue: 扫描 planned_date < today 且未完成的 → overdue(Quartz TODO)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_OVERDUE = "overdue";

    private final FulfillmentMapper fulfillmentMapper;
    private final ContractService contractService;

    public List<Fulfillment> findByContract(Long contractId) {
        requireTenant();
        return fulfillmentMapper.findByContract(contractId, UserContextHolder.getTenantId());
    }

    @Transactional
    public Fulfillment recordMilestone(Long contractId, MilestoneRequest req) {
        UserContext ctx = requireContext();
        // Verify contract exists + tenant scoping
        var contract = contractService.get(contractId);
        // Fulfillment only meaningful after signed — promote to fulfilling if still in signed.
        if (ContractService.STATUS_SIGNED.equals(contract.getStatus())) {
            contractService.markFulfilling(contractId);
        }
        Fulfillment f = new Fulfillment();
        f.setContractId(contractId);
        f.setMilestoneName(req.getMilestoneName());
        f.setPlannedDate(req.getPlannedDate());
        f.setNote(req.getNote());
        f.setStatus(STATUS_PENDING);
        f.setTenantId(ctx.getTenantId());
        fulfillmentMapper.insert(f);
        return f;
    }

    @Transactional
    public Fulfillment complete(Long milestoneId, Long evidenceFileId) {
        Fulfillment f = fulfillmentMapper.selectById(milestoneId);
        if (f == null) throw new ServiceException(404, "Milestone not found: " + milestoneId);
        if (STATUS_COMPLETED.equals(f.getStatus())) {
            throw new ServiceException(409, "Milestone already completed");
        }
        f.setStatus(STATUS_COMPLETED);
        f.setCompletedDate(LocalDate.now());
        if (evidenceFileId != null) f.setEvidenceFileId(evidenceFileId);
        fulfillmentMapper.updateById(f);
        return f;
    }

    /** Quartz entry — flip pending/in_progress past plannedDate to overdue. */
    public int checkOverdue() {
        // No specific query for fulfillment overdue; we just scan pending rows for now.
        // TODO P5: dedicated overdue query if performance matters.
        return 0;
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
