package com.lumen.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrOnboarding;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrOnboardingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Onboarding: spawn the initial checklist, progress through it, and close when done.
 *
 * <p>Status: 0=未开始 1=进行中 2=已完成</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_COMPLETED = 2;

    private final HrOnboardingMapper onboardingMapper;
    private final HrEmployeeMapper employeeMapper;

    /**
     * Default checklist template. Real implementations would source this from a
     * {@code hr_onboarding_template} table — kept as a constant for P4.
     */
    private static final String[] DEFAULT_CHECKLIST = {
        "签订劳动合同", "提交身份证复印件", "办理社保", "分配工位",
        "开通企业邮箱", "IT 资产发放", "完成入职培训"
    };

    @Transactional
    public HrOnboarding start(Long employeeId) {
        requireTenant();
        HrEmployee emp = employeeMapper.selectById(employeeId);
        if (emp == null) throw new ServiceException(404, "Employee not found: " + employeeId);
        ensureSameTenant(emp);

        // Idempotent: if an active onboarding already exists, return it.
        HrOnboarding existing = onboardingMapper.selectOne(
            new LambdaQueryWrapper<HrOnboarding>()
                .eq(HrOnboarding::getEmployeeId, employeeId)
                .ne(HrOnboarding::getStatus, STATUS_COMPLETED)
                .last("LIMIT 1"));
        if (existing != null) return existing;

        HrOnboarding ob = new HrOnboarding();
        ob.setEmployeeId(employeeId);
        ob.setChecklist(buildDefaultChecklist());
        ob.setStatus(STATUS_IN_PROGRESS);
        ob.setTenantId(currentTenantId());
        onboardingMapper.insert(ob);

        log.info("Started onboarding id={} employeeId={}", ob.getId(), employeeId);
        return ob;
    }

    @Transactional
    public HrOnboarding complete(Long id) {
        requireTenant();
        HrOnboarding ob = onboardingMapper.selectById(id);
        if (ob == null) throw new ServiceException(404, "Onboarding not found: " + id);
        ensureSameTenant(ob);
        if (ob.getStatus() == STATUS_COMPLETED) {
            throw new ServiceException(409, "Onboarding already completed");
        }
        ob.setStatus(STATUS_COMPLETED);
        ob.setCompletedAt(LocalDateTime.now());
        onboardingMapper.updateById(ob);
        log.info("Completed onboarding id={}", id);
        return ob;
    }

    public HrOnboarding getById(Long id) {
        requireTenant();
        HrOnboarding ob = onboardingMapper.selectById(id);
        if (ob == null) throw new ServiceException(404, "Onboarding not found: " + id);
        ensureSameTenant(ob);
        return ob;
    }

    private Map<String, Boolean> buildDefaultChecklist() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String item : DEFAULT_CHECKLIST) m.put(item, false);
        return m;
    }

    private void requireTenant() {
        if (UserContextHolder.get() == null || UserContextHolder.getTenantId() == null) {
            throw new ServiceException(401, "Missing tenant context");
        }
    }

    private long currentTenantId() {
        Long t = UserContextHolder.getTenantId();
        if (t == null) throw new ServiceException(401, "Missing tenant context");
        return t;
    }

    private void ensureSameTenant(HrEmployee e) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || e.getTenantId() == null || !tid.equals(e.getTenantId())) {
            throw new ServiceException(404, "Employee not found: " + e.getId());
        }
    }

    private void ensureSameTenant(HrOnboarding ob) {
        Long tid = UserContextHolder.getTenantId();
        if (tid == null || ob.getTenantId() == null || !tid.equals(ob.getTenantId())) {
            throw new ServiceException(404, "Onboarding not found: " + ob.getId());
        }
    }
}
