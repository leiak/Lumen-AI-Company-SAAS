package com.lumen.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Opportunity;
import com.lumen.sales.mapper.OpportunityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * 销售机会服务。状态机:
 * <pre>
 *   qualification -> proposal -> negotiation -> won/lost
 * </pre>
 * 单向跃迁;won/lost 不可再转。markWon 校验 stage=negotiation。markLost 校验 status=open。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityService {

    // stage 常量
    public static final String STAGE_QUALIFICATION = "qualification";
    public static final String STAGE_PROPOSAL = "proposal";
    public static final String STAGE_NEGOTIATION = "negotiation";
    public static final String STAGE_WON = "won";
    public static final String STAGE_LOST = "lost";

    // status 常量
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_WON = "won";
    public static final String STATUS_LOST = "lost";

    private static final Set<String> ALLOWED_STAGES = Set.of(
        STAGE_QUALIFICATION, STAGE_PROPOSAL, STAGE_NEGOTIATION, STAGE_WON, STAGE_LOST);

    /** Legal stage transitions (forward only). won/lost are terminal. */
    private static final Map<String, Set<String>> STAGE_TRANSITIONS = Map.of(
        STAGE_QUALIFICATION, Set.of(STAGE_PROPOSAL, STAGE_LOST),
        STAGE_PROPOSAL, Set.of(STAGE_NEGOTIATION, STAGE_LOST),
        STAGE_NEGOTIATION, Set.of(STAGE_WON, STAGE_LOST),
        STAGE_WON, Set.of(),
        STAGE_LOST, Set.of()
    );

    private final OpportunityMapper opportunityMapper;
    private final CustomerService customerService;

    public IPage<Opportunity> page(int pageNum, int pageSize, String stage, String status, Long ownerLevel) {
        UserContext ctx = requireContext();
        var w = new LambdaQueryWrapper<Opportunity>().orderByDesc(Opportunity::getId);
        if (stage != null && !stage.isBlank()) w.eq(Opportunity::getStage, stage);
        if (status != null && !status.isBlank()) w.eq(Opportunity::getStatus, status);
        boolean isAdminLike = customerService.hasAnyRole(ctx, "super_admin", "admin", "sales_admin");
        Long ownerFilter = ownerLevel;
        if (ownerFilter == null && !isAdminLike) {
            ownerFilter = ctx.getUserId();
        }
        if (ownerFilter != null) w.eq(Opportunity::getOwnerUserId, ownerFilter);
        return opportunityMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public Opportunity get(Long id) {
        Opportunity o = opportunityMapper.selectById(id);
        if (o == null) throw new ServiceException(404, "Opportunity not found: " + id);
        UserContext ctx = requireContext();
        if (!customerService.isSuperAdmin(ctx)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(o.getTenantId())) {
                throw new ServiceException(404, "Opportunity not found: " + id);
            }
        }
        return o;
    }

    @Transactional
    public Opportunity save(Opportunity req) {
        UserContext ctx = requireContext();
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        if (req.getCustomerId() == null) {
            throw new ServiceException(400, "customerId is required");
        }
        if (req.getAmount() == null) {
            throw new ServiceException(400, "amount is required");
        }
        if (req.getExpectedCloseDate() == null) {
            throw new ServiceException(400, "expectedCloseDate is required");
        }
        if (req.getProbability() != null && (req.getProbability() < 0 || req.getProbability() > 100)) {
            throw new ServiceException(400, "probability must be 0-100");
        }
        // customer must exist in current tenant
        customerService.get(req.getCustomerId());

        Opportunity toCreate = new Opportunity();
        toCreate.setName(req.getName());
        toCreate.setCustomerId(req.getCustomerId());
        toCreate.setLeadId(req.getLeadId());
        toCreate.setStage(req.getStage() == null ? STAGE_QUALIFICATION : req.getStage());
        toCreate.setAmount(req.getAmount());
        toCreate.setProbability(req.getProbability());
        toCreate.setExpectedCloseDate(req.getExpectedCloseDate());
        toCreate.setOwnerUserId(req.getOwnerUserId() == null ? ctx.getUserId() : req.getOwnerUserId());
        toCreate.setStatus(STATUS_OPEN);
        toCreate.setTenantId(ctx.getTenantId());
        opportunityMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public Opportunity update(Opportunity req) {
        if (req.getId() == null) throw new ServiceException(400, "id is required");
        Opportunity existing = get(req.getId());
        if (!STATUS_OPEN.equals(existing.getStatus())) {
            throw new ServiceException(409, "Closed opportunity cannot be edited");
        }
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getAmount() != null) existing.setAmount(req.getAmount());
        if (req.getProbability() != null) {
            if (req.getProbability() < 0 || req.getProbability() > 100) {
                throw new ServiceException(400, "probability must be 0-100");
            }
            existing.setProbability(req.getProbability());
        }
        if (req.getExpectedCloseDate() != null) existing.setExpectedCloseDate(req.getExpectedCloseDate());
        opportunityMapper.updateById(existing);
        return existing;
    }

    /**
     * 阶段跃迁 — 单向。qualification→proposal→negotiation→won/lost;不能跳级;不能从 won/lost 再转。
     */
    @Transactional
    public Opportunity stageUpdate(Long id, String newStage, java.time.LocalDate expectedCloseDate) {
        Opportunity o = get(id);
        if (newStage == null || !ALLOWED_STAGES.contains(newStage)) {
            throw new ServiceException(400, "Unknown target stage: " + newStage);
        }
        assertStageTransition(o.getStage(), newStage);
        if (expectedCloseDate != null) {
            o.setExpectedCloseDate(expectedCloseDate);
        }
        o.setStage(newStage);
        if (STAGE_WON.equals(newStage)) {
            o.setStatus(STATUS_WON);
        } else if (STAGE_LOST.equals(newStage)) {
            o.setStatus(STATUS_LOST);
        }
        opportunityMapper.updateById(o);
        return o;
    }

    /** Mark Won — 校验 stage=negotiation。 */
    @Transactional
    public Opportunity markWon(Long id) {
        Opportunity o = get(id);
        if (!STAGE_NEGOTIATION.equals(o.getStage())) {
            throw new ServiceException(409, "markWon requires stage=negotiation, current=" + o.getStage());
        }
        o.setStage(STAGE_WON);
        o.setStatus(STATUS_WON);
        opportunityMapper.updateById(o);
        return o;
    }

    /** Mark Lost — 校验 status=open。 */
    @Transactional
    public Opportunity markLost(Long id, String reason) {
        Opportunity o = get(id);
        if (!STATUS_OPEN.equals(o.getStatus())) {
            throw new ServiceException(409, "markLost requires status=open, current=" + o.getStatus());
        }
        o.setStage(STAGE_LOST);
        o.setStatus(STATUS_LOST);
        o.setCloseReason(reason);
        opportunityMapper.updateById(o);
        return o;
    }

    public void assertStageTransition(String from, String to) {
        if (from == null) {
            throw new ServiceException(409, "Current stage is null");
        }
        if (!ALLOWED_STAGES.contains(from)) {
            throw new ServiceException(409, "Unknown current stage: " + from);
        }
        if (!ALLOWED_STAGES.contains(to)) {
            throw new ServiceException(400, "Unknown target stage: " + to);
        }
        Set<String> allowed = STAGE_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ServiceException(409,
                "Illegal stage transition: " + from + " -> " + to);
        }
    }

    public UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }

    public Set<String> allowedStages() {
        return ALLOWED_STAGES;
    }
}