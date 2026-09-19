package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.GenerateBankFileRequest;
import com.lumen.payroll.entity.PayBankFile;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.mapper.PayBankFileMapper;
import com.lumen.payroll.mapper.PaySlipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 银行报盘文件服务。生成文本格式 (TODO P5 真实银行格式)。
 * 安全要求 #13: status=generated 后文件内容冻结, 不允许再修改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankFileService {

    public static final String STATUS_GENERATED = "generated";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_FAILED = "failed";

    private final PayBankFileMapper bankFileMapper;
    private final PaySlipMapper slipMapper;

    // ---------------------------------------------------------------
    // generate
    // ---------------------------------------------------------------

    /**
     * 生成指定 period + bankCode 的报盘文件。
     * 文件路径以文本格式存 (in-memory + 返回), status=generated 后不可改。
     */
    @Transactional
    public PayBankFile generate(GenerateBankFileRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        // 只对 confirmed (或 paid) 的 slip 出账
        List<PaySlip> slips = slipMapper.findByPeriod(req.getPeriod()).stream()
            .filter(s -> ctx.getTenantId().equals(s.getTenantId()))
            .filter(s -> PayslipService.STATUS_CONFIRMED.equals(s.getStatus())
                || PayslipService.STATUS_PAID.equals(s.getStatus()))
            .toList();
        if (slips.isEmpty()) {
            throw new ServiceException(400,
                "No confirmed/paid slips for period " + req.getPeriod());
        }
        int employeeCount = slips.size();
        BigDecimal total = slips.stream()
            .map(PaySlip::getNetSalary)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, java.math.RoundingMode.HALF_UP);

        // 幂等: 已存在 (tenant, period, bankCode) 则拒绝重新生成 (内容冻结)。
        PayBankFile existing = bankFileMapper.findByPeriodAndBankCode(req.getPeriod(), req.getBankCode());
        if (existing != null) {
            throw new ServiceException(409,
                "Bank file already generated for period=" + req.getPeriod()
                    + " bankCode=" + req.getBankCode()
                    + " status=" + existing.getStatus());
        }

        // 生成文本格式:
        // HEADER: BANK_CODE|PERIOD|COUNT|TOTAL|TIMESTAMP
        // 行:  SEQ|EMPLOYEE_ID|NET_SALARY
        // FOOTER: END
        String content = buildTextFile(req.getBankCode(), req.getPeriod(), slips);
        String md5 = md5(content);
        // 真实生产: 写 MinIO/S3, filePath 是 URL; 当前存占位符.
        String filePath = String.format("/payroll/%s/%s_%s.txt",
            req.getPeriod(), req.getBankCode(), md5.substring(0, 8));

        PayBankFile bf = new PayBankFile();
        bf.setTenantId(ctx.getTenantId());
        bf.setPeriod(req.getPeriod());
        bf.setBankCode(req.getBankCode());
        bf.setFilePath(filePath);
        bf.setFileMd5(md5);
        bf.setEmployeeCount(employeeCount);
        bf.setTotalAmount(total);
        bf.setStatus(STATUS_GENERATED);
        bf.setGeneratedAt(LocalDateTime.now());
        bankFileMapper.insert(bf);
        log.info("Bank file generated id={} period={} bankCode={} count={} total={} md5={}",
            bf.getId(), req.getPeriod(), req.getBankCode(), employeeCount, total, md5);
        return bf;
    }

    /**
     * 文本报盘格式 (示例)。
     * 真实生产: 各银行定制 (CCB 工行建行 等), 当前用统一占位格式 + TODO 注释。
     */
    public String buildTextFile(String bankCode, String period, List<PaySlip> slips) {
        StringBuilder sb = new StringBuilder();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        BigDecimal total = slips.stream()
            .map(PaySlip::getNetSalary)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, java.math.RoundingMode.HALF_UP);
        sb.append(String.format("HEADER|%s|%s|%d|%s|%s%n",
            bankCode, period, slips.size(), total.toPlainString(), ts));
        int seq = 1;
        for (PaySlip s : slips) {
            sb.append(String.format("%05d|%d|%s%n",
                seq++, s.getEmployeeId(), s.getNetSalary().toPlainString()));
        }
        sb.append("FOOTER|END|");
        sb.append(ts);
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 安全要求 #13: generated 后内容冻结, 此处再次生成会抛 409.
     */
    @Transactional
    public PayBankFile send(Long id) {
        UserContext ctx = requireUserContext();
        PayBankFile bf = getInternal(id, ctx.getTenantId());
        if (!STATUS_GENERATED.equals(bf.getStatus())) {
            throw new ServiceException(409,
                "Bank file can only be sent when status=generated; current status=" + bf.getStatus());
        }
        bf.setStatus(STATUS_SENT);
        bf.setSentAt(LocalDateTime.now());
        bankFileMapper.updateById(bf);
        // TODO P5: actual bank API integration.
        log.info("Bank file sent id={} (TODO P5 external API)", id);
        return bf;
    }

    @Transactional
    public PayBankFile markConfirmed(Long id) {
        UserContext ctx = requireUserContext();
        PayBankFile bf = getInternal(id, ctx.getTenantId());
        if (!STATUS_SENT.equals(bf.getStatus())) {
            throw new ServiceException(409,
                "Bank file can only be confirmed when status=sent; current status=" + bf.getStatus());
        }
        bf.setStatus(STATUS_CONFIRMED);
        bankFileMapper.updateById(bf);
        log.info("Bank file confirmed id={}", id);
        return bf;
    }

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    public List<PayBankFile> listByPeriod(String period) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return bankFileMapper.findByPeriod(period).stream()
            .filter(b -> ctx.getTenantId().equals(b.getTenantId()))
            .toList();
    }

    public List<PayBankFile> listByBankCode(String bankCode) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return bankFileMapper.findByBankCode(bankCode).stream()
            .filter(b -> ctx.getTenantId().equals(b.getTenantId()))
            .toList();
    }

    public PayBankFile get(Long id) {
        UserContext ctx = requireUserContext();
        return getInternal(id, ctx.getTenantId());
    }

    private PayBankFile getInternal(Long id, Long tenantId) {
        PayBankFile bf = bankFileMapper.selectById(id);
        if (bf == null || (tenantId != null && !tenantId.equals(bf.getTenantId()))) {
            throw new ServiceException(404, "Bank file not found: " + id);
        }
        return bf;
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new ServiceException(500, "MD5 unavailable", e);
        }
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}