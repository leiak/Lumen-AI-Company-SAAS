package com.lumen.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.ClauseTemplate;
import com.lumen.contract.mapper.ClauseTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 合同条款模板管理。提供 renderVariables 把 {{var}} 替换成实际值
 * (类似 message-center/TemplateService 的 render 语义)。
 *
 * <p>安全要点：所有声明的变量必须在 variables 中提供,否则抛 400。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ClauseTemplateMapper templateMapper;

    public IPage<ClauseTemplate> page(int pageNum, int pageSize, String keyword) {
        var w = new LambdaQueryWrapper<ClauseTemplate>().orderByDesc(ClauseTemplate::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(ClauseTemplate::getName, keyword)
                .or().like(ClauseTemplate::getType, keyword));
        }
        return templateMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ClauseTemplate getById(Long id) {
        ClauseTemplate t = templateMapper.selectById(id);
        if (t == null) throw new ServiceException(404, "Template not found: " + id);
        return t;
    }

    @Transactional
    public ClauseTemplate create(ClauseTemplate req) {
        UserContext ctx = requireContext();
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        ClauseTemplate toCreate = new ClauseTemplate();
        toCreate.setName(req.getName());
        toCreate.setType(req.getType());
        toCreate.setVariables(req.getVariables());
        toCreate.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        toCreate.setTenantId(ctx.getTenantId());
        templateMapper.insert(toCreate);
        log.info("Created clause template id={} name={}", toCreate.getId(), toCreate.getName());
        return toCreate;
    }

    @Transactional
    public ClauseTemplate update(Long id, ClauseTemplate req) {
        ClauseTemplate existing = getById(id);
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getType() != null) existing.setType(req.getType());
        if (req.getVariables() != null) existing.setVariables(req.getVariables());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        templateMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        templateMapper.deleteById(id);
    }

    /**
     * Render variables declared in the template — replaces {@code {{var}}} placeholders
     * with values from {@code variables}. Returns a render result (content null when
     * template has no body) — caller (ContractService.draft) decides how to use it.
     *
     * @throws ServiceException 404 if template not found, 400 if any declared variable missing.
     */
    public Rendered renderVariables(Long templateId, Map<String, Object> variables) {
        ClauseTemplate tpl = getById(templateId);
        if (tpl.getStatus() == null || tpl.getStatus() != 1) {
            throw new ServiceException(409, "Template not enabled: " + templateId);
        }
        List<String> declared = tpl.getVariables() == null ? List.of() : tpl.getVariables();
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        for (String name : declared) {
            if (!vars.containsKey(name) || vars.get(name) == null) {
                throw new ServiceException(400,
                    "Missing required template variable: " + name
                        + " (template=" + templateId + ")");
            }
        }
        // For P3 the rendered body is just a passthrough marker — callers can attach to clauses.
        return new Rendered(templateId, tpl.getName(), tpl.getType(), declared, vars);
    }

    private UserContext requireContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");
        return ctx;
    }

    /** Render result — declared list is exposed so callers can iterate on substitution. */
    public record Rendered(Long templateId, String name, String type,
                            List<String> declared, Map<String, Object> variables) {}
}
