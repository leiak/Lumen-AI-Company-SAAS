package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.CalculateSocialSecurityRequest;
import com.lumen.payroll.entity.PaySocialSecurity;
import com.lumen.payroll.mapper.PaySocialSecurityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 社保服务。
 *
 * <p>个人 + 公司部分: 五险 (养老/医疗/失业/工伤/生育) + 公积金,
 * 按"基数 × 比例"分别计算 (按典型城市默认比例, 实际生产应通过租户配置调整)。</p>
 *
 * <p>基数上下限: [minBase, maxBase] (默认 [3613, 31884], 北京 2024 上限), service 校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSecurityService {

    public static final String STATUS_CALCULATED = "calculated";
    public static final String STATUS_DECLARED = "declared";
    public static final String STATUS_PAID = "paid";

    /** 社保基数下限 (北京 2024 最低 ≈ 3613) */
    public static final BigDecimal MIN_BASE = new BigDecimal("3613.00");
    /** 社保基数上限 (北京 2024 最高 ≈ 31884) */
    public static final BigDecimal MAX_BASE = new BigDecimal("31884.00");

    /**
     * 默认比例 (个人 / 公司). 实际生产应通过租户配置覆盖.
     * Key: 项目名; 个人比例; 公司比例.
     */
    private static final Map<String, BigDecimal[]> RATES = Map.of(
        "pension",      new BigDecimal[]{new BigDecimal("0.08"),  new BigDecimal("0.16")},
        "medical",      new BigDecimal[]{new BigDecimal("0.02"),  new BigDecimal("0.10")},
        "unemployment", new BigDecimal[]{new BigDecimal("0.005"), new BigDecimal("0.005")},
        "injury",       new BigDecimal[]{BigDecimal.ZERO,         new BigDecimal("0.01")},
        "maternity",    new BigDecimal[]{BigDecimal.ZERO,         new BigDecimal("0.008")},
        "housingFund",  new BigDecimal[]{new BigDecimal("0.07"),  new BigDecimal("0.07")}
    );

    private final PaySocialSecurityMapper mapper;

    /**
     * 计算并保存单员工当月社保。安全要求 #12: 校验 baseAmount 在 [minBase, maxBase] 范围。
     */
    @Transactional
    public PaySocialSecurity calculate(CalculateSocialSecurityRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        BigDecimal base = req.getBaseAmount();
        if (base.compareTo(MIN_BASE) < 0 || base.compareTo(MAX_BASE) > 0) {
            throw new ServiceException(400,
                "baseAmount out of range [" + MIN_BASE + ", " + MAX_BASE + "]: " + base);
        }

        BigDecimal employeeSum = BigDecimal.ZERO;
        BigDecimal employerSum = BigDecimal.ZERO;
        Map<String, Object> items = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal[]> e : RATES.entrySet()) {
            String name = e.getKey();
            BigDecimal empRate = e.getValue()[0];
            BigDecimal comRate = e.getValue()[1];
            BigDecimal emp = base.multiply(empRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal com = base.multiply(comRate).setScale(2, RoundingMode.HALF_UP);
            items.put(name, Map.of(
                "employee", emp,
                "employer", com,
                "employeeRate", empRate,
                "employerRate", comRate
            ));
            employeeSum = employeeSum.add(emp);
            employerSum = employerSum.add(com);
        }
        employeeSum = employeeSum.setScale(2, RoundingMode.HALF_UP);
        employerSum = employerSum.setScale(2, RoundingMode.HALF_UP);

        // 幂等: 已存在则 update
        PaySocialSecurity existing = mapper.findByEmployeeAndPeriod(req.getEmployeeId(), req.getPeriod());
        PaySocialSecurity s = existing != null ? existing : new PaySocialSecurity();
        if (existing == null) {
            s.setTenantId(ctx.getTenantId());
            s.setEmployeeId(req.getEmployeeId());
            s.setPeriod(req.getPeriod());
        }
        s.setBaseAmount(base);
        s.setEmployeeAmount(employeeSum);
        s.setEmployerAmount(employerSum);
        s.setItems(items);
        s.setStatus(STATUS_CALCULATED);
        if (existing == null) {
            mapper.insert(s);
        } else {
            mapper.updateById(s);
        }
        log.info("Social security calculated employeeId={} period={} empAmount={} comAmount={}",
            req.getEmployeeId(), req.getPeriod(), employeeSum, employerSum);
        return s;
    }

    /**
     * 整月批量申报。安全要求 #10 类: 计算后才能申报.
     * TODO P5: 调外部社保系统接口.
     */
    @Transactional
    public int declare(String period) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        if (!isValidPeriod(period)) {
            throw new ServiceException(400, "period must be yyyy-MM format");
        }
        List<PaySocialSecurity> list = mapper.findByPeriod(period);
        int count = 0;
        for (PaySocialSecurity s : list) {
            if (STATUS_CALCULATED.equals(s.getStatus())) {
                s.setStatus(STATUS_DECLARED);
                mapper.updateById(s);
                count++;
            }
        }
        log.info("Social security declared period={} count={}", period, count);
        // TODO P5: external integration
        return count;
    }

    public List<PaySocialSecurity> listByPeriod(String period) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return mapper.findByPeriod(period).stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .toList();
    }

    /**
     * 内部用: 算薪循环取单员工当月社保个人部分 (deduction)。
     * 缺失时返回 0 (按"未参保"处理, 不阻塞算薪)。
     */
    public BigDecimal employeeAmount(Long employeeId, String period) {
        PaySocialSecurity s = mapper.findByEmployeeAndPeriod(employeeId, period);
        return s == null || s.getEmployeeAmount() == null
            ? BigDecimal.ZERO
            : s.getEmployeeAmount();
    }

    private boolean isValidPeriod(String period) {
        return period != null && period.matches("^\\d{4}-(0[1-9]|1[0-2])$");
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}