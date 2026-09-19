package com.lumen.assets.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.assets.dto.CategoryNode;
import com.lumen.assets.entity.AstCategory;
import com.lumen.assets.mapper.AstCategoryMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产分类树形 CRUD。path 用 "/id/id/..." 物化路径，便于子树查询。
 *
 * <p>Tenant 隔离：第一行要求 UserContextHolder 已设置，否则 401。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    public static final long ROOT_PARENT_ID = 0L;

    private final AstCategoryMapper categoryMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    /** 整棵树（按 parent_id + path 排序）。 */
    public List<CategoryNode> tree() {
        requireCtx();
        List<AstCategory> all = categoryMapper.selectList(
            new LambdaQueryWrapper<AstCategory>().orderByAsc(AstCategory::getPath));
        return buildTree(all);
    }

    /** 按 id 查询；跨租户 → 404。 */
    public AstCategory getById(Long id) {
        UserContext ctx = requireCtx();
        AstCategory c = categoryMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Category not found: " + id);
        if (!c.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Category not found: " + id);
        }
        return c;
    }

    @Transactional
    public AstCategory create(AstCategory req) {
        UserContext ctx = requireCtx();
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        Long parentId = req.getParentId() == null ? ROOT_PARENT_ID : req.getParentId();
        AstCategory parent = null;
        if (parentId != ROOT_PARENT_ID) {
            parent = getById(parentId); // 校验存在 + 跨租户 404
        }
        AstCategory toCreate = new AstCategory();
        toCreate.setTenantId(ctx.getTenantId());
        toCreate.setParentId(parentId);
        toCreate.setCode(req.getCode());
        toCreate.setName(req.getName());
        toCreate.setLevel(parent == null ? 1 : (parent.getLevel() == null ? 1 : parent.getLevel() + 1));
        toCreate.setDepreciationMethodDefault(req.getDepreciationMethodDefault());
        toCreate.setUsefulLifeDefault(req.getUsefulLifeDefault());
        try {
            categoryMapper.insert(toCreate);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Category code conflict", ex);
        }
        // path 在插入后回填（依赖自增 id）
        toCreate.setPath(buildPath(parent, toCreate.getId()));
        categoryMapper.updateById(toCreate);
        log.info("Category created id={} code={} parent={}", toCreate.getId(), toCreate.getCode(), parentId);
        return toCreate;
    }

    @Transactional
    public AstCategory update(Long id, AstCategory req) {
        AstCategory existing = getById(id);
        if (req.getCode() != null) existing.setCode(req.getCode());
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getDepreciationMethodDefault() != null) {
            existing.setDepreciationMethodDefault(req.getDepreciationMethodDefault());
        }
        if (req.getUsefulLifeDefault() != null) {
            existing.setUsefulLifeDefault(req.getUsefulLifeDefault());
        }
        categoryMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        AstCategory existing = getById(id);
        // 不能删除有子节点的分类
        Long childCount = categoryMapper.selectCount(
            new LambdaQueryWrapper<AstCategory>().eq(AstCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new ServiceException(409, "Category has children; remove them first");
        }
        categoryMapper.deleteById(id);
    }

    private static String buildPath(AstCategory parent, Long newId) {
        if (parent == null || parent.getPath() == null || parent.getPath().isEmpty()) {
            return "/" + newId;
        }
        return parent.getPath() + "/" + newId;
    }

    /** 把扁平的分类列表拼成树（按 path 字典序即天然按层级）。 */
    private static List<CategoryNode> buildTree(List<AstCategory> all) {
        Map<Long, CategoryNode> map = new HashMap<>();
        for (AstCategory c : all) {
            map.put(c.getId(), CategoryNode.from(c));
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (AstCategory c : all) {
            CategoryNode node = map.get(c.getId());
            if (c.getParentId() == null || c.getParentId() == ROOT_PARENT_ID) {
                roots.add(node);
            } else {
                CategoryNode p = map.get(c.getParentId());
                if (p != null) {
                    p.getChildren().add(node);
                } else {
                    // 父节点被删或租户不一致 → 当作孤立根（不静默吞）
                    roots.add(node);
                }
            }
        }
        return roots;
    }
}