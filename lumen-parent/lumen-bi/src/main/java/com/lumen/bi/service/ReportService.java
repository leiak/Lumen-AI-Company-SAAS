package com.lumen.bi.service;

import com.lumen.bi.dto.SaveReportRequest;
import com.lumen.bi.entity.BiReport;
import com.lumen.bi.mapper.BiReportMapper;
import com.lumen.bi.util.CronValidator;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表服务。CRUD + runNow 立即执行 + schedule 计算下次执行时间 + sendReport 邮件发送 (TODO)。
 *
 * <p>安全要求 #9: schedule 用 cron-utils 校验合法性。
 * 安全要求 #14: cron 频率必须 >= 1h (CronValidator.MIN_INTERVAL_MINUTES)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    public static final String SCHEDULE_MANUAL = "manual";
    public static final String SCHEDULE_DAILY = "daily";
    public static final String SCHEDULE_WEEKLY = "weekly";
    public static final String SCHEDULE_MONTHLY = "monthly";

    public static final String FORMAT_PDF = "pdf";
    public static final String FORMAT_EXCEL = "excel";
    public static final String FORMAT_CSV = "csv";

    private final BiReportMapper reportMapper;

    // ---------------------------------------------------------------
    // read
    // ---------------------------------------------------------------

    public BiReport get(Long id) {
        UserContext ctx = requireUserContext();
        BiReport r = reportMapper.selectById(id);
        if (r == null || (ctx.getTenantId() != null && !ctx.getTenantId().equals(r.getTenantId()))) {
            throw new ServiceException(404, "Report not found: " + id);
        }
        return r;
    }

    public List<BiReport> list() {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        return reportMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BiReport>()
                .eq(BiReport::getTenantId, ctx.getTenantId())
                .eq(BiReport::getDeleted, 0)
                .orderByDesc(BiReport::getId));
    }

    public List<BiReport> findBySchedule(String schedule) {
        UserContext ctx = requireUserContext();
        List<BiReport> all = reportMapper.findBySchedule(schedule);
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(r -> ctx.getTenantId().equals(r.getTenantId()))
            .toList();
    }

    public List<BiReport> findActive() {
        UserContext ctx = requireUserContext();
        List<BiReport> all = reportMapper.findActive();
        if (ctx.getTenantId() == null) return all;
        return all.stream()
            .filter(r -> ctx.getTenantId().equals(r.getTenantId()))
            .toList();
    }

    // ---------------------------------------------------------------
    // write
    // ---------------------------------------------------------------

    @Transactional
    public BiReport save(SaveReportRequest req) {
        UserContext ctx = requireUserContext();
        if (ctx.getTenantId() == null) {
            throw new ServiceException(401, "No tenant in context");
        }
        // 安全要求 #9/#14: schedule != manual 时 cron 必须存在且合法
        if (!SCHEDULE_MANUAL.equals(req.getSchedule())) {
            if (req.getCronExpression() == null || req.getCronExpression().isBlank()) {
                throw new ServiceException(400,
                    "cronExpression is required when schedule != manual");
            }
            CronValidator.validate(req.getCronExpression());
        }

        BiReport existing = reportMapper.findByCodeAndTenant(req.getCode(), ctx.getTenantId());
        if (existing != null) {
            if (req.getName() != null) existing.setName(req.getName());
            if (req.getDatasetId() != null) existing.setDatasetId(req.getDatasetId());
            if (req.getTemplate() != null) existing.setTemplate(req.getTemplate());
            if (req.getSchedule() != null) existing.setSchedule(req.getSchedule());
            if (req.getCronExpression() != null) existing.setCronExpression(req.getCronExpression());
            if (req.getFormat() != null) existing.setFormat(req.getFormat());
            if (req.getRecipients() != null) existing.setRecipients(req.getRecipients());
            existing.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
            reportMapper.updateById(existing);
            return existing;
        }
        BiReport r = new BiReport();
        r.setTenantId(ctx.getTenantId());
        r.setCode(req.getCode());
        r.setName(req.getName());
        r.setDatasetId(req.getDatasetId());
        r.setTemplate(req.getTemplate());
        r.setSchedule(req.getSchedule());
        r.setCronExpression(req.getCronExpression());
        r.setFormat(req.getFormat());
        r.setRecipients(req.getRecipients());
        r.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        reportMapper.insert(r);
        return r;
    }

    // ---------------------------------------------------------------
    // runNow
    // ---------------------------------------------------------------

    /**
     * 立即执行报表生成。TODO P5: 接 DatasetService.preview 生成实际文件 (pdf/excel/csv)。
     * 返回 { reportId, format, executedAt, rowCount }。
     */
    public Map<String, Object> runNow(Long id, String format) {
        BiReport r = get(id);
        if (!STATUS_ACTIVE.equals(r.getStatus())) {
            throw new ServiceException(409, "Report is not active: " + id);
        }
        String useFormat = format == null ? r.getFormat() : format;
        // 校验 format
        if (useFormat == null
            || !(FORMAT_PDF.equals(useFormat) || FORMAT_EXCEL.equals(useFormat) || FORMAT_CSV.equals(useFormat))) {
            throw new ServiceException(400, "invalid format: " + useFormat);
        }
        r.setLastRunAt(LocalDateTime.now());
        reportMapper.updateById(r);

        Map<String, Object> result = new HashMap<>();
        result.put("reportId", id);
        result.put("format", useFormat);
        result.put("executedAt", r.getLastRunAt());
        result.put("rowCount", 0); // TODO P5: 真实数据集行数
        log.info("Report runNow id={} format={}", id, useFormat);
        return result;
    }

    // ---------------------------------------------------------------
    // schedule — 计算下次执行时间
    // ---------------------------------------------------------------

    /**
     * 安全要求 #9: 计算下次执行时间。
     * TODO P5: 接 Quartz 真实调度 (当前只在内存计算 nextFireTime)。
     */
    public Map<String, Object> schedule(Long id) {
        BiReport r = get(id);
        if (SCHEDULE_MANUAL.equals(r.getSchedule())) {
            throw new ServiceException(409, "Manual report has no schedule: " + id);
        }
        if (r.getCronExpression() == null || r.getCronExpression().isBlank()) {
            throw new ServiceException(409, "Report has no cron expression: " + id);
        }
        CronValidator.validate(r.getCronExpression());
        LocalDateTime next = CronValidator.nextFireTime(r.getCronExpression());

        Map<String, Object> result = new HashMap<>();
        result.put("reportId", id);
        result.put("schedule", r.getSchedule());
        result.put("cronExpression", r.getCronExpression());
        result.put("nextFireTime", next);
        return result;
    }

    // ---------------------------------------------------------------
    // sendReport — 通过 email 发送 (TODO P5: 调 message-center)
    // ---------------------------------------------------------------

    public Map<String, Object> sendReport(Long id, List<String> recipients) {
        BiReport r = get(id);
        List<String> targets = recipients == null || recipients.isEmpty() ? r.getRecipients() : recipients;
        if (targets == null || targets.isEmpty()) {
            throw new ServiceException(400, "No recipients");
        }
        // TODO P5: 调 message-center /msg/send 发送
        log.info("Report sendReport id={} recipients={}", id, targets);

        Map<String, Object> result = new HashMap<>();
        result.put("reportId", id);
        result.put("recipients", targets);
        result.put("status", "queued");
        return result;
    }

    @Transactional
    public void delete(Long id) {
        BiReport r = get(id);
        reportMapper.deleteById(r.getId());
    }

    private UserContext requireUserContext() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ServiceException(401, "No user context");
        }
        return ctx;
    }
}