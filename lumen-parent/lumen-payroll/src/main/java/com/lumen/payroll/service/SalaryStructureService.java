package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.SaveStructureRequest;
import com.lumen.payroll.entity.PaySalaryStructure;
import com.lumen.payroll.mapper.PaySalaryStructureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 薪资结构服务。CRUD + JSON 校验 (components 必须有 baseSalary key)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryStructureService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private final PaySalaryStructureMapper mapper;

    /**
     * 安全要求 #3 (跨租户 404).
     */
    public PaySalaryStructure get(Long id) {
        UserContext ctx = requireUserContext();
        PaySalaryStructure s = mapper.selectById(id);
        if (s == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(s.getTenantId()))) {
            throw new ServiceException(404, "Salary structure not found: " + id);
        }
        return s;
    }

    public List<PaySalaryStructure> list() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return mapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaySalaryStructure>()
                .eq(PaySalaryStructure::getTenantId, ctx.getTenantId())
                .eq(PaySalaryStructure::getDeleted, 0)
                .orderByDesc(PaySalaryStructure::getId));
    }

    public List<PaySalaryStructure> findActive() {
        UserContext ctx = requireUserContext();
        List<PaySalaryStructure> all = mapper.findActive();
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .toList();
    }

    @Transactional
    public PaySalaryStructure save(SaveStructureRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        // 安全要求: components 必须含 baseSalary
        if (req.getComponents() == null || req.getComponents().isEmpty()) {
            throw new ServiceException(400, "components must not be empty");
        }
        if (!req.getComponents().containsKey("baseSalary")) {
            throw new ServiceException(400, "components must contain baseSalary key");
        }
        Object base = req.getComponents().get("baseSalary");
        if (!(base instanceof Number) || ((Number) base).doubleValue() <= 0) {
            throw new ServiceException(400, "components.baseSalary must be positive number");
        }

        PaySalaryStructure existing = mapper.findByCodeAndTenant(req.getCode(), ctx.getTenantId());
        if (existing != null) {
            existing.setName(req.getName());
            existing.setComponents(req.getComponents());
            existing.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
            mapper.updateById(existing);
            log.info("Salary structure updated id={} code={}", existing.getId(), existing.getCode());
            return existing;
        }

        PaySalaryStructure s = new PaySalaryStructure();
        s.setTenantId(ctx.getTenantId());
        s.setCode(req.getCode());
        s.setName(req.getName());
        s.setComponents(req.getComponents());
        s.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        mapper.insert(s);
        log.info("Salary structure saved id={} code={}", s.getId(), s.getCode());
        return s;
    }

    @Transactional
    public void delete(Long id) {
        PaySalaryStructure s = get(id);
        mapper.deleteById(s.getId());
        log.info("Salary structure deleted id={}", id);
    }

    /**
     * 内部用 (其他 service 调用):按 id 加载结构, 无 ctx 校验 (因为已在 caller tenant 内).
     * 缺失或跨租户 → 抛 404.
     */
    public PaySalaryStructure getInternal(Long id, Long tenantId) {
        PaySalaryStructure s = mapper.selectById(id);
        if (s == null || (tenantId != null && !tenantId.equals(s.getTenantId()))) {
            throw new ServiceException(404, "Salary structure not found: " + id);
        }
        return s;
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}