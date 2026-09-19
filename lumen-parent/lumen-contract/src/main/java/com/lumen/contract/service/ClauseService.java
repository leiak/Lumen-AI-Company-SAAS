package com.lumen.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.Clause;
import com.lumen.contract.entity.Contract;
import com.lumen.contract.mapper.ClauseMapper;
import com.lumen.contract.mapper.ContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同条款管理。bulkSave 是替换语义:先删旧再插新,事务包裹。
 * 安全要求:合同状态必须在 drafting 才能修改条款 — approved 之后内容锁定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClauseService {

    private final ClauseMapper clauseMapper;
    private final ContractMapper contractMapper;

    public List<Clause> findByContract(Long contractId) {
        requireTenant();
        // Tenant scoping via mapper injection — TenantLineInnerInterceptor handles
        // tenant_id WHERE clause automatically.
        return clauseMapper.findByContract(contractId, UserContextHolder.getTenantId());
    }

    public Clause get(Long id) {
        Clause c = clauseMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Clause not found: " + id);
        return c;
    }

    @Transactional
    public Clause save(Clause req) {
        UserContext ctx = requireContext();
        // Validate contract exists + status check (approved = locked)
        Contract contract = contractMapper.selectById(req.getContractId());
        if (contract == null) {
            throw new ServiceException(404, "Contract not found: " + req.getContractId());
        }
        if (ContractService.STATUS_APPROVED.equals(contract.getStatus())
            || ContractService.STATUS_SIGNING.equals(contract.getStatus())
            || ContractService.STATUS_SIGNED.equals(contract.getStatus())
            || ContractService.STATUS_FULFILLING.equals(contract.getStatus())
            || ContractService.STATUS_ARCHIVED.equals(contract.getStatus())) {
            throw new ServiceException(409,
                "Cannot edit clauses after approval (status=" + contract.getStatus() + ")");
        }
        Clause toCreate = new Clause();
        toCreate.setContractId(req.getContractId());
        toCreate.setClauseNo(req.getClauseNo());
        toCreate.setTitle(req.getTitle());
        toCreate.setContent(req.getContent());
        toCreate.setOrderNum(req.getOrderNum() == null ? 0 : req.getOrderNum());
        toCreate.setSource(req.getSource() == null ? "manual" : req.getSource());
        toCreate.setTenantId(ctx.getTenantId());
        clauseMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public Clause update(Long id, Clause req) {
        Clause existing = get(id);
        Contract contract = contractMapper.selectById(existing.getContractId());
        if (contract != null && !ContractService.STATUS_DRAFTING.equals(contract.getStatus())
            && !ContractService.STATUS_PENDING_APPROVAL.equals(contract.getStatus())) {
            throw new ServiceException(409,
                "Cannot edit clauses after approval (status=" + contract.getStatus() + ")");
        }
        if (req.getClauseNo() != null) existing.setClauseNo(req.getClauseNo());
        if (req.getTitle() != null) existing.setTitle(req.getTitle());
        if (req.getContent() != null) existing.setContent(req.getContent());
        if (req.getOrderNum() != null) existing.setOrderNum(req.getOrderNum());
        clauseMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        clauseMapper.deleteById(id);
    }

    /**
     * Replace all clauses for a contract — transactional. First deletes the existing
     * rows (logic delete via BaseEntity) then inserts the new ones in declared order.
     *
     * <p>原子性: 整个替换操作在单一事务内完成,失败回滚 → 不会留下"删了旧的没插新的"的中间态。</p>
     */
    @Transactional
    public int bulkSave(Long contractId, List<Clause> clauses) {
        UserContext ctx = requireContext();
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException(404, "Contract not found: " + contractId);
        }
        if (!ContractService.STATUS_DRAFTING.equals(contract.getStatus())
            && !ContractService.STATUS_PENDING_APPROVAL.equals(contract.getStatus())) {
            throw new ServiceException(409,
                "Cannot bulk-save clauses after approval (status=" + contract.getStatus() + ")");
        }
        if (clauses == null) clauses = List.of();
        // 1) Mark all existing rows as deleted
        clauseMapper.delete(new LambdaQueryWrapper<Clause>()
            .eq(Clause::getContractId, contractId));
        // 2) Insert new ones (orderNum preserved from list index)
        int order = 0;
        for (Clause c : clauses) {
            Clause toInsert = new Clause();
            toInsert.setContractId(contractId);
            toInsert.setClauseNo(c.getClauseNo());
            toInsert.setTitle(c.getTitle());
            toInsert.setContent(c.getContent());
            toInsert.setOrderNum(c.getOrderNum() != null ? c.getOrderNum() : order);
            toInsert.setSource(c.getSource() == null ? "manual" : c.getSource());
            toInsert.setTenantId(ctx.getTenantId());
            clauseMapper.insert(toInsert);
            order++;
        }
        log.info("Bulk-saved {} clauses for contract={}", clauses.size(), contractId);
        return clauses.size();
    }

    /**
     * Auto-seed a starter clause from a template render. Called by ContractService.draft
     * — produces one placeholder clause carrying the rendered variable snapshot.
     */
    @Transactional
    public void autoSeedFromTemplate(Long contractId, TemplateService.Rendered rendered) {
        UserContext ctx = requireContext();
        Clause seed = new Clause();
        seed.setContractId(contractId);
        seed.setClauseNo("TPL-1");
        seed.setTitle(rendered.name());
        seed.setContent("Template variables: " + (rendered.variables() == null
            ? "{}" : new HashMap<>(rendered.variables()).toString()));
        seed.setOrderNum(0);
        seed.setSource("template");
        seed.setTenantId(ctx.getTenantId());
        clauseMapper.insert(seed);
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
