package com.lumen.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.workflow.entity.WfDefinition;
import com.lumen.workflow.mapper.WfDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefinitionService {

    private final WfDefinitionMapper definitionMapper;

    /** Status constants (mirror {@link EngineService}). */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_OFFLINE = 2;

    public IPage<WfDefinition> page(int pageNum, int pageSize, String keyword, String category) {
        var w = new LambdaQueryWrapper<WfDefinition>().orderByDesc(WfDefinition::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(WfDefinition::getDefKey, keyword)
                .or().like(WfDefinition::getName, keyword));
        }
        if (category != null && !category.isBlank()) {
            w.eq(WfDefinition::getCategory, category);
        }
        return definitionMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public WfDefinition getById(Long id) {
        WfDefinition d = definitionMapper.selectById(id);
        if (d == null) throw new ServiceException(404, "Definition not found: " + id);
        return d;
    }

    /**
     * Create a new draft definition. If {@code defKey} already exists, the new version
     * is {@code MAX(version)+1}; otherwise starts at 1.
     */
    @Transactional
    public WfDefinition create(WfDefinition req) {
        if (req.getDefKey() == null || req.getDefKey().isBlank()) {
            throw new ServiceException(400, "defKey is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }

        Integer nextVersion = nextVersion(req.getDefKey());

        // Field-by-field copy — strip client-controlled fields (id, createBy, etc.)
        WfDefinition toCreate = new WfDefinition();
        toCreate.setDefKey(req.getDefKey());
        toCreate.setName(req.getName());
        toCreate.setVersion(nextVersion);
        toCreate.setCategory(req.getCategory());
        toCreate.setBpmnXml(req.getBpmnXml());
        toCreate.setStatus(STATUS_DRAFT);
        try {
            definitionMapper.insert(toCreate);
        } catch (DuplicateKeyException ex) {
            // Two concurrent creates with the same defKey both computed the same nextVersion.
            throw new ServiceException(409, "Definition version conflict", ex);
        }
        log.info("Created definition id={} defKey={} version={}",
            toCreate.getId(), toCreate.getDefKey(), toCreate.getVersion());
        return toCreate;
    }

    @Transactional
    public WfDefinition update(WfDefinition req) {
        if (req.getId() == null) throw new ServiceException(400, "id is required");
        WfDefinition existing = getById(req.getId());
        if (existing.getStatus() != null && existing.getStatus() == STATUS_PUBLISHED) {
            throw new ServiceException(409, "Cannot edit published definition; create a new version");
        }
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getCategory() != null) existing.setCategory(req.getCategory());
        if (req.getBpmnXml() != null) existing.setBpmnXml(req.getBpmnXml());
        definitionMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        WfDefinition existing = getById(id);
        if (existing.getStatus() != null && existing.getStatus() == STATUS_PUBLISHED) {
            throw new ServiceException(409, "Cannot delete published definition");
        }
        definitionMapper.deleteById(id);
    }

    /** Mark definition as published so {@link EngineService#startInstance} will pick it up. */
    @Transactional
    public WfDefinition deploy(Long id) {
        WfDefinition existing = getById(id);
        existing.setStatus(STATUS_PUBLISHED);
        definitionMapper.updateById(existing);
        log.info("Deployed definition id={} defKey={}", id, existing.getDefKey());
        return existing;
    }

    private Integer nextVersion(String defKey) {
        WfDefinition latest = definitionMapper.selectLatestByKey(defKey);
        return (latest == null || latest.getVersion() == null) ? 1 : latest.getVersion() + 1;
    }
}