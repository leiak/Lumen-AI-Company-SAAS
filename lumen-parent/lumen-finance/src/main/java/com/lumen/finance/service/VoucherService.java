package com.lumen.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.finance.dto.SaveVoucherRequest;
import com.lumen.finance.dto.VoucherEntryDto;
import com.lumen.finance.entity.FinPeriod;
import com.lumen.finance.entity.FinVoucher;
import com.lumen.finance.entity.FinVoucherEntry;
import com.lumen.finance.mapper.FinPeriodMapper;
import com.lumen.finance.mapper.FinVoucherEntryMapper;
import com.lumen.finance.mapper.FinVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 凭证服务。
 *
 * <p>状态机：{@code draft -> posted -> reversed}。
 * draft 可编辑；posted 不可编辑；reversed 是终态。post 必校验借贷平衡 +
 * 期间 open；reverse 生成一张反向凭证 + 把原 voucher 置为 reversed。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_POSTED = "posted";
    public static final String STATUS_REVERSED = "reversed";

    private final FinVoucherMapper voucherMapper;
    private final FinVoucherEntryMapper entryMapper;
    private final FinPeriodMapper periodMapper;

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    public List<FinVoucher> findByPeriod(String period) {
        requireTenant();
        return voucherMapper.findByPeriod(period);
    }

    /**
     * 安全要求 #3: 跨租户 → 404。
     */
    public FinVoucher get(Long id) {
        UserContext ctx = requireUserContext();
        FinVoucher v = voucherMapper.selectById(id);
        if (v == null) {
            throw new ServiceException(404, "Voucher not found: " + id);
        }
        assertTenant(v, ctx);
        return v;
    }

    public List<FinVoucherEntry> entries(Long voucherId) {
        get(voucherId); // tenant check
        return entryMapper.findByVoucher(voucherId);
    }

    // ---------------------------------------------------------------
    // create / save
    // ---------------------------------------------------------------

    @Transactional
    public FinVoucher save(SaveVoucherRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "tenantId missing in context");
        }
        if (req.getEntries() == null || req.getEntries().isEmpty()) {
            throw new ServiceException(400, "entries must not be empty");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (VoucherEntryDto e : req.getEntries()) {
            // XOR check: 不能同时 > 0, 也不能同时为 0/null
            boolean d = e.getDebitAmount() != null && e.getDebitAmount().signum() > 0;
            boolean c = e.getCreditAmount() != null && e.getCreditAmount().signum() > 0;
            if (d == c) {
                throw new ServiceException(400,
                    "Voucher entry must specify exactly one of debit/credit > 0 (subjectId="
                        + e.getSubjectId() + ")");
            }
            totalDebit = totalDebit.add(d ? e.getDebitAmount() : BigDecimal.ZERO);
            totalCredit = totalCredit.add(c ? e.getCreditAmount() : BigDecimal.ZERO);
        }
        totalDebit = totalDebit.setScale(2, RoundingMode.HALF_UP);
        totalCredit = totalCredit.setScale(2, RoundingMode.HALF_UP);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ServiceException(400,
                "Voucher not balanced: debit=" + totalDebit + " credit=" + totalCredit);
        }

        FinVoucher v = new FinVoucher();
        v.setTenantId(ctx.getTenantId());
        v.setVoucherNo(generateVoucherNo());
        v.setPeriod(req.getPeriod());
        v.setVoucherDate(req.getVoucherDate());
        v.setSummary(req.getSummary());
        v.setTotalDebit(totalDebit);
        v.setTotalCredit(totalCredit);
        v.setStatus(STATUS_DRAFT);
        try {
            voucherMapper.insert(v);
        } catch (DuplicateKeyException ex) {
            // safety net: uk_voucher_no_tenant
            throw new ServiceException(409, "VoucherNo conflict", ex);
        }

        for (VoucherEntryDto e : req.getEntries()) {
            FinVoucherEntry ent = new FinVoucherEntry();
            ent.setTenantId(ctx.getTenantId());
            ent.setVoucherId(v.getId());
            ent.setSubjectId(e.getSubjectId());
            ent.setDebitAmount(e.getDebitAmount() == null ? BigDecimal.ZERO : e.getDebitAmount());
            ent.setCreditAmount(e.getCreditAmount() == null ? BigDecimal.ZERO : e.getCreditAmount());
            ent.setSummary(e.getSummary());
            entryMapper.insert(ent);
        }
        log.info("Voucher saved id={} no={} period={}", v.getId(), v.getVoucherNo(), v.getPeriod());
        return v;
    }

    // ---------------------------------------------------------------
    // post / reverse — 状态机
    // ---------------------------------------------------------------

    /**
     * 过账。安全要求 #4: 借贷必平;安全要求 #5: posted 不能再 edit
     * (此处表现为: 状态变更前已计算平衡, posted 后所有 save 拒绝重写);
     * 安全要求 #7: 期间 closed/locked 后不能 post。
     */
    @Transactional
    public FinVoucher post(Long id) {
        UserContext ctx = requireUserContext();
        FinVoucher v = get(id);

        if (!STATUS_DRAFT.equals(v.getStatus())) {
            throw new ServiceException(409,
                "Only draft vouchers can be posted; current status=" + v.getStatus());
        }
        // 安全要求 #4: 借贷必平 (重新计算以防历史脏数据)
        List<FinVoucherEntry> entries = entryMapper.findByVoucher(v.getId());
        BigDecimal d = entries.stream()
            .map(FinVoucherEntry::getDebitAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal c = entries.stream()
            .map(FinVoucherEntry::getCreditAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        if (d.compareTo(c) != 0) {
            throw new ServiceException(400,
                "Voucher not balanced: debit=" + d + " credit=" + c);
        }
        // 安全要求 #7: period must be open
        FinPeriod period = requirePeriod(v.getPeriod());
        if (!"open".equals(period.getStatus())) {
            throw new ServiceException(409,
                "Period is not open: " + v.getPeriod() + " status=" + period.getStatus());
        }

        v.setStatus(STATUS_POSTED);
        v.setPostedAt(LocalDateTime.now());
        v.setPostedBy(ctx.getUserId());
        voucherMapper.updateById(v);
        log.info("Voucher posted id={} period={} by={}", v.getId(), v.getPeriod(), ctx.getUserId());
        return v;
    }

    /**
     * 反过账。安全要求 #6: 必须 reference 原 voucher。
     * 生成一张反向凭证（借贷对调），原 voucher 标记 reversed。
     */
    @Transactional
    public FinVoucher reverse(Long id, String reason) {
        UserContext ctx = requireUserContext();
        FinVoucher orig = get(id);

        if (!STATUS_POSTED.equals(orig.getStatus())) {
            throw new ServiceException(409,
                "Only posted vouchers can be reversed; current status=" + orig.getStatus());
        }
        FinPeriod period = requirePeriod(orig.getPeriod());
        if ("locked".equals(period.getStatus())) {
            throw new ServiceException(403, "Period is locked; cannot reverse");
        }
        if (reason == null || reason.isBlank()) {
            throw new ServiceException(400, "reason is required");
        }

        // Build the reversal voucher first so we can reference it from orig.
        FinVoucher rev = new FinVoucher();
        rev.setTenantId(ctx.getTenantId());
        rev.setVoucherNo(generateVoucherNo());
        rev.setPeriod(orig.getPeriod());
        rev.setVoucherDate(orig.getVoucherDate());
        rev.setSummary("reverse of " + orig.getVoucherNo() + ": " + reason);
        rev.setTotalDebit(orig.getTotalCredit());
        rev.setTotalCredit(orig.getTotalDebit());
        rev.setStatus(STATUS_POSTED);  // 反向凭证直接视为已过账
        rev.setPostedAt(LocalDateTime.now());
        rev.setPostedBy(ctx.getUserId());
        rev.setReversedId(orig.getId());
        voucherMapper.insert(rev);

        // Mirror entries with swapped debit/credit.
        List<FinVoucherEntry> src = entryMapper.findByVoucher(orig.getId());
        for (FinVoucherEntry s : src) {
            FinVoucherEntry r = new FinVoucherEntry();
            r.setTenantId(ctx.getTenantId());
            r.setVoucherId(rev.getId());
            r.setSubjectId(s.getSubjectId());
            r.setDebitAmount(s.getCreditAmount());
            r.setCreditAmount(s.getDebitAmount());
            r.setSummary(s.getSummary());
            entryMapper.insert(r);
        }

        orig.setStatus(STATUS_REVERSED);
        orig.setReversedId(rev.getId());
        voucherMapper.updateById(orig);
        log.info("Voucher reversed id={} -> reversalId={} reason={}", orig.getId(), rev.getId(), reason);
        return rev;
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private FinPeriod requirePeriod(String period) {
        FinPeriod p = periodMapper.findByYearMonth(
            Integer.parseInt(period.substring(0, 4)),
            Integer.parseInt(period.substring(5, 7)));
        if (p == null) {
            throw new ServiceException(404, "Period not found: " + period);
        }
        return p;
    }

    private String generateVoucherNo() {
        // VoucherNo 唯一性靠 UNIQUE(voucher_no, tenant_id, deleted) 保证;
        // 这里用时间戳 + UUID 段避免高并发冲突。
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "V-" + ts + "-" + rand;
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

    private void assertTenant(FinVoucher v, UserContext ctx) {
        if (ctx.getTenantId() == null || !ctx.getTenantId().equals(v.getTenantId())) {
            throw new ServiceException(404, "Voucher not found: " + v.getId());
        }
    }
}
