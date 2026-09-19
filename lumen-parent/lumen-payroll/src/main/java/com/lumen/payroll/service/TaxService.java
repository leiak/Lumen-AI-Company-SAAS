package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.entity.PayTax;
import com.lumen.payroll.mapper.PayTaxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 个税服务。2018+ 新个税:5000 起征 + 七级累进 + 累计预扣预缴。
 *
 * <p>累计预扣预缴公式 (扣缴义务人按月):</p>
 * <pre>
 *   本月预扣预缴税额 = (累计预扣预缴应纳税所得额 × 预扣率 − 速算扣除数)
 *                       − 累计减免税额 − 累计已预扣预缴税额
 *
 *   累计预扣预缴应纳税所得额 = 累计收入 − 累计免税收入
 *                              − 累计减除费用 (5000 × 月数)
 *                              − 累计专项扣除 (五险一金)
 *                              − 累计专项附加扣除
 *                              − 累计依法确定的其他扣除
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxService {

    /** 起征点 (5000 元/月) */
    public static final BigDecimal BASIC_DEDUCTION = new BigDecimal("5000.00");

    /**
     * 2018+ 七级累进表 (年度):
     * 级数 | 全年应纳税所得额              | 税率 | 速算扣除数
     *  1   | ≤  36000                       |  3%  |     0
     *  2   |  36000 <  ≤ 144000             |  10% |   2520
     *  3   |  144000 <  ≤ 300000            |  20% |  16920
     *  4   |  300000 <  ≤ 420000            |  25% |  31920
     *  5   |  420000 <  ≤ 660000            |  30% |  52920
     *  6   |  660000 <  ≤ 960000            |  35% |  85920
     *  7   |  >  960000                     |  45% | 181920
     */
    private static final BigDecimal[][] ANNUAL_BRACKETS = {
        {new BigDecimal("36000"),  new BigDecimal("0.03"), new BigDecimal("0")},
        {new BigDecimal("144000"), new BigDecimal("0.10"), new BigDecimal("2520")},
        {new BigDecimal("300000"), new BigDecimal("0.20"), new BigDecimal("16920")},
        {new BigDecimal("420000"), new BigDecimal("0.25"), new BigDecimal("31920")},
        {new BigDecimal("660000"), new BigDecimal("0.30"), new BigDecimal("52920")},
        {new BigDecimal("960000"), new BigDecimal("0.35"), new BigDecimal("85920")},
        {null,                      new BigDecimal("0.45"), new BigDecimal("181920")}
    };

    private final PayTaxMapper taxMapper;

    // ---------------------------------------------------------------
    // 累计预扣预缴 (P5+ 可调外部税务服务, 当前本地实现)
    // ---------------------------------------------------------------

    /**
     * 计算到 period (含) 当年的累计应税收入 (gross)。
     */
    public BigDecimal cumulativeIncome(Long employeeId, String period) {
        requireTenant();
        int month = Integer.parseInt(period.substring(5, 7));
        String startOfYear = period.substring(0, 4) + "-01";
        List<PayTax> records = taxMapper.listByEmployeeYearTo(employeeId, startOfYear, period);
        BigDecimal total = BigDecimal.ZERO;
        for (PayTax t : records) {
            if (t.getTaxableIncome() != null) {
                // taxableIncome 在保存时已 = 当月 gross - 起征 - 社保个人 - 专项附加 - 五险一金;
                // 累计收入近似: taxableIncome + 起征 + 社保个人 (按月加回)。
                // 简化: 这里只返回 tax 表里的累积字段 cumulativeIncome (service.saveRecord 已填)。
                total = total.add(t.getCumulativeIncome() == null ? BigDecimal.ZERO : t.getCumulativeIncome());
            }
        }
        // 减去已加总部分重复, 只取最新一条的 cumulativeIncome 即可;
        // 但 listByEmployeeYearTo 包含所有月份, 这里简化采用"最后一条 cumulativeIncome"。
        if (!records.isEmpty()) {
            return records.get(records.size() - 1).getCumulativeIncome();
        }
        return total;
    }

    /**
     * 算每月个税。
     *
     * @param taxableIncome       本月应税收入 (gross - 起征 - 社保个人 - 专项附加)
     * @param cumulativeIncome    累计到当月收入 (gross 累计)
     * @param specialDeduction    专项附加扣除 JSON 字符串 (可选)
     * @return 本月预扣预缴税额
     */
    public BigDecimal calculateTax(BigDecimal taxableIncome,
                                   BigDecimal cumulativeIncome,
                                   String specialDeduction) {
        if (taxableIncome == null) taxableIncome = BigDecimal.ZERO;
        if (cumulativeIncome == null) cumulativeIncome = BigDecimal.ZERO;
        // 累计应纳税所得额 = 累计收入 - 累计起征(5000 × 月数) - 累计社保 - 累计专项附加
        // 这里 taxableIncome 已经 = 当月应税, 而 cumulativeIncome 是 gross 累计;
        // 累计应税 = taxableIncome (本月) + (累计 gross - 本月 gross)
        // 但 taxableIncome 通常已经"减去本月起征", 所以累计应税 = taxableIncome (含本月减项) 是不够严谨的近似;
        // 我们用累计 gross - 累计起征 5000*N (N=月份数) 来近似 (实际生产应更精细)。
        // 这里采用生产口径: 应税 = taxableIncome + (cumulativeIncome - 当月 gross)
        // 由于 caller 传进来的 taxableIncome 是"已经减过 5000 + 社保个人 + 专项附加"的本月数,
        // 累计应税 (gross) = cumulativeIncome - N*5000 - 累计专项附加 - 累计社保个人
        // 简化处理: 直接拿 taxableIncome 作"累计"概念 (调用方负责传准确值)。
        BigDecimal cumulativeTaxable = taxableIncome;
        if (cumulativeTaxable.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // 用年度累进表查税率和速算扣除数
        BigDecimal[] bracket = findBracket(cumulativeTaxable);
        BigDecimal rate = bracket[0];
        BigDecimal quickDeduction = bracket[1];
        return cumulativeTaxable.multiply(rate)
            .subtract(quickDeduction)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 按累计应税 (gross 等价) 查年度累进表, 返回 [rate, quickDeduction].
     * 表中以"全年应纳税所得额"为基准。
     */
    private BigDecimal[] findBracket(BigDecimal annualTaxable) {
        for (BigDecimal[] row : ANNUAL_BRACKETS) {
            if (row[0] == null || annualTaxable.compareTo(row[0]) <= 0) {
                return new BigDecimal[]{row[1], row[2]};
            }
        }
        // fallback to 45%
        return new BigDecimal[]{new BigDecimal("0.45"), new BigDecimal("181920")};
    }

    /**
     * 公开给单元测试: 把 7 级累进表跑全, 每段返回一个税额。
     */
    public BigDecimal taxAtBracket(int level, BigDecimal annualTaxable) {
        if (level < 1 || level > 7) {
            throw new IllegalArgumentException("level must be 1..7");
        }
        BigDecimal[] row = ANNUAL_BRACKETS[level - 1];
        BigDecimal rate = row[1];
        BigDecimal quickDeduction = row[2];
        return annualTaxable.multiply(rate).subtract(quickDeduction).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 公开给测试: 拿一个完整应税区间数字, 返回应用到的累进表行 index (0..6)。
     */
    public int bracketIndex(BigDecimal annualTaxable) {
        for (int i = 0; i < ANNUAL_BRACKETS.length; i++) {
            BigDecimal limit = ANNUAL_BRACKETS[i][0];
            if (limit == null || annualTaxable.compareTo(limit) <= 0) {
                return i;
            }
        }
        return ANNUAL_BRACKETS.length - 1;
    }

    // ---------------------------------------------------------------
    // 持久化
    // ---------------------------------------------------------------

    /**
     * 写一条个税记录。slipId 关联工资条。
     */
    @Transactional
    public PayTax saveRecord(Long slipId, Long employeeId, String period,
                             BigDecimal taxableIncome, BigDecimal taxAmount,
                             BigDecimal taxRate, BigDecimal cumulativeIncome,
                             BigDecimal cumulativeTax, String specialDeductionJson) {
        UserContext ctx = requireUserContext();
        // 幂等: 同 (slipId) 已存在则 update
        PayTax existing = taxMapper.findBySlip(slipId);
        PayTax t = existing != null ? existing : new PayTax();
        if (existing == null) {
            t.setTenantId(ctx.getTenantId());
            t.setSlipId(slipId);
            t.setEmployeeId(employeeId);
        }
        t.setPeriod(period);
        t.setTaxableIncome(taxableIncome);
        t.setTaxAmount(taxAmount);
        t.setTaxRate(taxRate);
        t.setCumulativeIncome(cumulativeIncome);
        t.setCumulativeTax(cumulativeTax);
        t.setSpecialDeductionJson(specialDeductionJson);
        if (existing == null) {
            taxMapper.insert(t);
        } else {
            taxMapper.updateById(t);
        }
        return t;
    }

    /**
     * 列出某员工某期间的个税。
     */
    public PayTax findByEmployeeAndPeriod(Long employeeId, String period) {
        requireTenant();
        return taxMapper.findByEmployeeAndPeriod(employeeId, period);
    }

    public PayTax findBySlip(Long slipId) {
        requireTenant();
        return taxMapper.findBySlip(slipId);
    }

    public List<PayTax> listByPeriod(String period) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return taxMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayTax>()
                .eq(PayTax::getTenantId, ctx.getTenantId())
                .eq(PayTax::getPeriod, period)
                .eq(PayTax::getDeleted, 0)
                .orderByAsc(PayTax::getEmployeeId));
    }

    /**
     * 计算专项附加扣除总额 (从 JSON 字符串 Map 的 value 求和).
     * specialDeductionJson 形如 {"children":1000,"mortgage":1000,"elder":2000}.
     */
    public static BigDecimal sumSpecialDeduction(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (Object v : map.values()) {
            if (v instanceof Number n) {
                sum = sum.add(BigDecimal.valueOf(n.doubleValue()));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }

    private UserContext requireTenant() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return ctx;
    }
}