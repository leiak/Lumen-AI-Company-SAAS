package com.lumen.bi.util;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.lumen.common.core.exception.ServiceException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Cron 表达式校验器 (cron-utils)。
 *
 * <p>安全要求 #9 / #14:
 * - 使用 cron-utils 校验合法性 (Spring 6-field: 秒 分 时 日 月 周)
 * - 拒绝频率 < 1h 的 cron (防止 DoS / 资源耗尽)</p>
 */
public final class CronValidator {

    private static final CronParser PARSER = new CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));

    /** 最小间隔 (分钟) — 60 表示不允许 1 分钟一次 */
    public static final int MIN_INTERVAL_MINUTES = 60;

    private CronValidator() {}

    /**
     * 校验 cron 表达式合法, 且触发频率 >= MIN_INTERVAL_MINUTES。
     */
    public static void validate(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new ServiceException(400, "cronExpression is empty");
        }
        Cron cron;
        try {
            cron = PARSER.parse(cronExpression);
            cron.validate();
        } catch (Exception e) {
            throw new ServiceException(400,
                "invalid cron expression: " + cronExpression + " (" + e.getMessage() + ")");
        }
        // 频率检查: 计算 next 5 次触发时间间隔, 任一间隔 < 1h 则拒绝
        ExecutionTime et = ExecutionTime.forCron(cron);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime prev = now;
        int samples = 5;
        for (int i = 0; i < samples; i++) {
            Optional<ZonedDateTime> nextOpt = et.nextExecution(prev);
            if (nextOpt.isEmpty()) break;
            ZonedDateTime next = nextOpt.get();
            long minutes = java.time.Duration.between(prev, next).toMinutes();
            if (minutes < MIN_INTERVAL_MINUTES) {
                throw new ServiceException(400,
                    "cron frequency too high: " + minutes + "min < " + MIN_INTERVAL_MINUTES + "min");
            }
            prev = next;
        }
    }

    /**
     * 计算下次执行时间 (近似: 用 cron-utils ExecutionTime)。
     * TODO P5: 接入 Quartz 真实调度。
     */
    public static LocalDateTime nextFireTime(String cronExpression) {
        try {
            Cron cron = PARSER.parse(cronExpression);
            ExecutionTime et = ExecutionTime.forCron(cron);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            Optional<ZonedDateTime> next = et.nextExecution(now);
            return next.isEmpty() ? null : next.get().toLocalDateTime();
        } catch (Exception e) {
            throw new ServiceException(400,
                "cron parse failed: " + cronExpression);
        }
    }
}