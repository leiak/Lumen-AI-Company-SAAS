package com.lumen.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.workflow.entity.WfInstance;
import com.lumen.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceService {

    private final WfInstanceMapper instanceMapper;
    private final EngineService engineService;

    /** Delegates to {@link EngineService#startInstance}. */
    public Long start(String defKey, String businessKey, Map<String, Object> variables) {
        return engineService.startInstance(defKey, businessKey, variables);
    }

    public WfInstance get(Long id) {
        WfInstance i = instanceMapper.selectById(id);
        if (i == null) throw new ServiceException(404, "Instance not found: " + id);
        return i;
    }

    public IPage<WfInstance> pageByCurrentUser(int pageNum, int pageSize) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new ServiceException(401, "No user context");
        }
        return instanceMapper.selectPage(
            Page.of(pageNum, pageSize),
            new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getStarter, userId)
                .orderByDesc(WfInstance::getId));
    }

    @Transactional
    public WfInstance cancel(Long id, String reason) {
        WfInstance instance = get(id);
        if (instance.getStatus() != EngineService.INSTANCE_STATUS_RUNNING) {
            throw new ServiceException(409,
                "Only running instances can be cancelled; current status=" + instance.getStatus());
        }
        engineService.closeInstanceCancelled(instance, reason);
        log.info("Cancelled instance id={} reason={}", id, reason);
        return instance;
    }
}