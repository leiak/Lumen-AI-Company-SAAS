package com.lumen.contract.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.contract.entity.ChangeLog;
import com.lumen.contract.mapper.ChangeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 合同变更日志 — ContractService 每个写方法都调 {@link #record}。
 * 当前以手动调用为主(显式比 AOP 更可控),后续可换成事件驱动。
 *
 * <p>Mandatory record() sites:
 * ContractService.save / draft / submit / approve / markSigning / markSigned /
 * markFulfilling / terminate / archive — see {@link ContractService}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeLogService {

    private final ChangeLogMapper changeLogMapper;

    @Transactional
    public ChangeLog record(Long contractId, String changeType,
                            Map<String, Object> beforeValue, Map<String, Object> afterValue,
                            String comment) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) throw new ServiceException(401, "No user context");
        if (ctx.getTenantId() == null) throw new ServiceException(401, "Missing tenant context");

        ChangeLog log = new ChangeLog();
        log.setContractId(contractId);
        log.setChangeType(changeType);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setOperatorId(ctx.getUserId());
        log.setOperatedAt(LocalDateTime.now());
        log.setComment(comment);
        log.setTenantId(ctx.getTenantId());
        changeLogMapper.insert(log);
        return log;
    }

    public List<ChangeLog> findByContract(Long contractId) {
        if (UserContextHolder.get() == null || UserContextHolder.get().getTenantId() == null) {
            throw new ServiceException(401, "No tenant context");
        }
        return changeLogMapper.findByContract(contractId, UserContextHolder.getTenantId());
    }
}
