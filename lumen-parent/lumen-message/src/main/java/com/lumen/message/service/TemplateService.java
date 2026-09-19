package com.lumen.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.message.entity.MsgTemplate;
import com.lumen.message.mapper.MsgTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final MsgTemplateMapper templateMapper;

    public IPage<MsgTemplate> page(int pageNum, int pageSize, String keyword) {
        var w = new LambdaQueryWrapper<MsgTemplate>().orderByDesc(MsgTemplate::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(MsgTemplate::getCode, keyword)
                .or().like(MsgTemplate::getSubject, keyword));
        }
        return templateMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public MsgTemplate getById(Long id) {
        MsgTemplate t = templateMapper.selectById(id);
        if (t == null) throw new ServiceException(404, "Template not found: " + id);
        return t;
    }

    public MsgTemplate create(MsgTemplate req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getChannelCode() == null || req.getChannelCode().isBlank()) {
            throw new ServiceException(400, "channelCode is required");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new ServiceException(400, "content is required");
        }
        if (req.getEnabled() == null) req.setEnabled(1);
        MsgTemplate toCreate = new MsgTemplate();
        toCreate.setCode(req.getCode());
        toCreate.setChannelCode(req.getChannelCode());
        toCreate.setSubject(req.getSubject());
        toCreate.setContent(req.getContent());
        toCreate.setVariables(req.getVariables());
        toCreate.setEnabled(req.getEnabled());
        templateMapper.insert(toCreate);
        log.info("Created template id={} code={} channelCode={}",
            toCreate.getId(), toCreate.getCode(), toCreate.getChannelCode());
        return toCreate;
    }

    public MsgTemplate update(Long id, MsgTemplate req) {
        MsgTemplate existing = getById(id);
        if (req.getChannelCode() != null) existing.setChannelCode(req.getChannelCode());
        if (req.getSubject() != null) existing.setSubject(req.getSubject());
        if (req.getContent() != null) existing.setContent(req.getContent());
        if (req.getVariables() != null) existing.setVariables(req.getVariables());
        if (req.getEnabled() != null) existing.setEnabled(req.getEnabled());
        templateMapper.updateById(existing);
        return existing;
    }

    public void delete(Long id) {
        getById(id);
        templateMapper.deleteById(id);
    }

    /**
     * Find a template by (code, channelCode, tenantId). Returns null if missing.
     */
    public MsgTemplate findByCodeAndChannel(String code, String channelCode, Long tenantId) {
        if (code == null || code.isBlank()) return null;
        if (channelCode == null || channelCode.isBlank()) return null;
        if (tenantId == null) return null;
        return templateMapper.findByCodeAndChannel(code, channelCode, tenantId);
    }

    /**
     * Render the template by replacing {@code {{varName}}} with values from
     * {@code variables}. Only declared variables (template.variables) are
     * substituted — anything else in {@code variables} is ignored. This is
     * security-relevant: it bounds the substitution surface and prevents
     * unintended template fragments from being filled.
     *
     * <p>Hardening applied:</p>
     * <ul>
     *   <li>All declared variables MUST be present in {@code variables} —
     *       missing → 400 BEFORE rendering (never leave {{name}} literal). 安全 #6</li>
     *   <li>Values are coerced to string via {@code String.valueOf}; nulls are
     *       treated as missing → 400.</li>
     * </ul>
     *
     * @return rendered subject + content (subject null if template has none)
     */
    public Rendered render(String templateCode, String channelCode,
                           Map<String, Object> variables, Long tenantId) {
        MsgTemplate tpl = findByCodeAndChannel(templateCode, channelCode, tenantId);
        if (tpl == null || tpl.getEnabled() == null || tpl.getEnabled() != 1) {
            throw new ServiceException(404, "Template not found: " + templateCode + "@" + channelCode);
        }

        // 安全 #6: every declared variable must be supplied BEFORE we render.
        List<String> declared = tpl.getVariables() == null ? java.util.List.of() : tpl.getVariables();
        Map<String, Object> vars = variables == null ? java.util.Map.of() : variables;
        for (String name : declared) {
            if (!vars.containsKey(name) || vars.get(name) == null) {
                throw new ServiceException(400,
                    "Missing required template variable: " + name
                        + " (template=" + templateCode + ")");
            }
        }

        String renderedSubject = tpl.getSubject() == null ? null
            : renderString(tpl.getSubject(), declared, vars);
        String renderedContent = renderString(tpl.getContent(), declared, vars);
        return new Rendered(tpl.getCode(), tpl.getChannelCode(), renderedSubject, renderedContent);
    }

    private static String renderString(String template, List<String> declared,
                                        Map<String, Object> vars) {
        String out = template;
        for (String name : declared) {
            String value = String.valueOf(vars.get(name));
            out = out.replace("{{" + name + "}}", value);
        }
        return out;
    }

    /** Rendered template — subject may be null (channel templates without subject). */
    public record Rendered(String code, String channelCode, String subject, String content) {}
}