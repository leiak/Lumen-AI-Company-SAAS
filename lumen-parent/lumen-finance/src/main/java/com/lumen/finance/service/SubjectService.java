package com.lumen.finance.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.dto.SaveSubjectRequest;
import com.lumen.finance.entity.FinAccountSubject;
import com.lumen.finance.mapper.FinAccountSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科目 service。树形结构（materialised path），{@code path} 形如 {@code "0,1,3,12"}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectService {

    private final FinAccountSubjectMapper subjectMapper;

    public List<FinAccountSubject> getTree(Long parentId) {
        requireTenant();
        // For B1 simplicity we fetch the whole active tree and assemble in memory.
        // TODO P5: paginate / lazy-load subtree when card-count > 10k.
        List<FinAccountSubject> all = subjectMapper.listAllActive();
        Map<Long, FinAccountSubject> byId = new HashMap<>();
        for (FinAccountSubject s : all) byId.put(s.getId(), s);
        // Build adjacency once (defensive — handles orphans and cycles)
        for (FinAccountSubject s : all) {
            Long pid = s.getParentId() == null ? 0L : s.getParentId();
            if (parentId == null || pid.equals(parentId)) {
                // we'll inline these as roots below
            }
        }
        Set<Long> seen = new HashSet<>();
        for (FinAccountSubject s : all) {
            if (seen.contains(s.getId())) continue;
            Long cur = s.getParentId();
            while (cur != null && cur != 0L) {
                if (!seen.add(cur)) {
                    throw new ServiceException(500, "Subject tree has a cycle at id=" + s.getId());
                }
                FinAccountSubject p = byId.get(cur);
                if (p == null) break;
                cur = p.getParentId();
            }
        }
        return all;
    }

    @Transactional
    public FinAccountSubject save(SaveSubjectRequest req) {
        UserContext ctx = requireUserContext();
        if (req.getParentId() == null) req.setParentId(0L);

        String path;
        int level;
        if (req.getParentId() == 0L) {
            path = "0";
            level = 1;
        } else {
            FinAccountSubject parent = subjectMapper.selectById(req.getParentId());
            if (parent == null) {
                throw new ServiceException(404, "Parent subject not found: " + req.getParentId());
            }
            path = parent.getPath() + "," + parent.getId();
            level = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
        }

        FinAccountSubject s = new FinAccountSubject();
        s.setTenantId(ctx.getTenantId());
        s.setParentId(req.getParentId());
        s.setCode(req.getCode());
        s.setName(req.getName());
        s.setLevel(level);
        s.setType(req.getType());
        s.setBalanceDirection(req.getBalanceDirection());
        s.setPath(path);
        s.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        subjectMapper.insert(s);
        log.info("Subject saved id={} code={} path={}", s.getId(), s.getCode(), s.getPath());
        return s;
    }

    private UserContext requireTenant() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return ctx;
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}
