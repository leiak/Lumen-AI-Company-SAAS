package com.lumen.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.org.dto.DeptNode;
import com.lumen.org.entity.SysDept;
import com.lumen.org.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeptService {

    private final SysDeptMapper deptMapper;

    public List<DeptNode> tree() {
        List<SysDept> all = deptMapper.listAllActive();
        Map<Long, DeptNode> nodeMap = new HashMap<>();
        Map<Long, SysDept> rawMap = new HashMap<>();
        for (SysDept d : all) {
            nodeMap.put(d.getDeptId(), DeptNode.from(d));
            rawMap.put(d.getDeptId(), d);
        }
        List<DeptNode> roots = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (SysDept d : all) {
            DeptNode node = nodeMap.get(d.getDeptId());
            if (d.getParentId() == null || d.getParentId() == 0L) {
                roots.add(node);
            } else {
                DeptNode parent = nodeMap.get(d.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // Walk ancestors to detect cycle and find a safe root
                    Long cur = d.getParentId();
                    Set<Long> chain = new HashSet<>();
                    chain.add(d.getDeptId());
                    boolean cycled = false;
                    while (cur != null && cur != 0L && !cycled) {
                        if (!chain.add(cur)) { cycled = true; break; }
                        SysDept ancestor = rawMap.get(cur);
                        if (ancestor == null) { cur = null; break; }
                        cur = ancestor.getParentId();
                    }
                    if (cycled) {
                        throw new ServiceException(500, "Dept tree has a cycle starting at id=" + d.getDeptId());
                    }
                    // Orphaned — keep at root level for visibility
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    public IPage<SysDept> list(int pageNum, int pageSize, String keyword) {
        var w = new LambdaQueryWrapper<SysDept>()
            .eq(SysDept::getStatus, "0")
            .orderByAsc(SysDept::getOrderNum);
        if (keyword != null && !keyword.isBlank()) {
            w.like(SysDept::getDeptName, keyword);
        }
        return deptMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public SysDept getById(Long id) {
        SysDept d = deptMapper.selectById(id);
        if (d == null) throw new ServiceException(404, "Dept not found: " + id);
        return d;
    }

    @Transactional
    public SysDept create(SysDept dept) {
        if (dept.getDeptName() == null || dept.getDeptName().isBlank()) {
            throw new ServiceException(400, "deptName is required");
        }
        if (dept.getParentId() == null) dept.setParentId(0L);
        if (dept.getStatus() == null) dept.setStatus("0");
        if (dept.getOrderNum() == null) dept.setOrderNum(0);

        // Compute ancestors
        String ancestors;
        if (dept.getParentId() == 0L) {
            ancestors = "0";
        } else {
            SysDept parent = getById(dept.getParentId());
            ancestors = parent.getAncestors() + "," + parent.getDeptId();
        }

        // Build a fresh entity — do NOT copy client-controlled fields
        SysDept toCreate = new SysDept();
        toCreate.setParentId(dept.getParentId());
        toCreate.setAncestors(ancestors);
        toCreate.setDeptName(dept.getDeptName());
        toCreate.setDeptCategory(dept.getDeptCategory());
        toCreate.setOrderNum(dept.getOrderNum());
        toCreate.setLeader(dept.getLeader());
        toCreate.setLeaderName(dept.getLeaderName());
        toCreate.setPhone(dept.getPhone());
        toCreate.setEmail(dept.getEmail());
        toCreate.setStatus(dept.getStatus());
        toCreate.setRemark(dept.getRemark());
        // isBuiltin defaults to 0 (null); tenantId set by TenantLineInnerInterceptor
        deptMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public SysDept update(SysDept dept) {
        SysDept existing = getById(dept.getDeptId());
        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            throw new ServiceException(403, "Cannot modify builtin dept");
        }
        boolean parentChanged = !java.util.Objects.equals(existing.getParentId(), dept.getParentId());

        // Capture old ancestors BEFORE overwrite
        String oldAncestors = existing.getAncestors();
        String oldPrefix = oldAncestors + "," + existing.getDeptId();

        // Compute new ancestors if parent changed
        if (parentChanged && dept.getParentId() != null) {
            if (dept.getParentId() == 0L) {
                existing.setAncestors("0");
            } else {
                SysDept parent = getById(dept.getParentId());
                // Prevent moving under self or own descendant
                if (parent.getAncestors() != null && parent.getAncestors().contains("," + existing.getDeptId())) {
                    throw new ServiceException(400, "Cannot move dept under its own descendant");
                }
                existing.setAncestors(parent.getAncestors() + "," + parent.getDeptId());
            }
            existing.setParentId(dept.getParentId());
        }

        String newPrefix = existing.getAncestors() + "," + existing.getDeptId();

        // Copy mutable fields from request
        existing.setDeptName(dept.getDeptName());
        existing.setOrderNum(dept.getOrderNum());
        existing.setLeaderName(dept.getLeaderName());
        existing.setPhone(dept.getPhone());
        existing.setEmail(dept.getEmail());
        existing.setStatus(dept.getStatus());
        if (dept.getDeptCategory() != null) existing.setDeptCategory(dept.getDeptCategory());
        if (dept.getLeader() != null) existing.setLeader(dept.getLeader());
        if (dept.getRemark() != null) existing.setRemark(dept.getRemark());

        deptMapper.updateById(existing);

        // If parent changed, rewrite descendants' ancestors
        if (parentChanged && !oldPrefix.equals(newPrefix)) {
            List<SysDept> descendants = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .likeRight(SysDept::getAncestors, oldPrefix + ","));
            for (SysDept d : descendants) {
                String updated = newPrefix + d.getAncestors().substring(oldPrefix.length());
                d.setAncestors(updated);
                deptMapper.updateById(d);
            }
        }
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        SysDept existing = getById(id);
        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            throw new ServiceException(403, "Cannot delete builtin dept");
        }
        // Check children
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
            .eq(SysDept::getParentId, id));
        if (childCount > 0) {
            throw new ServiceException(409, "Dept has children; delete them first");
        }
        deptMapper.deleteById(id);
    }
}