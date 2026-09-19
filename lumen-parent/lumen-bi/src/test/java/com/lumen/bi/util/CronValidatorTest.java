package com.lumen.bi.util;

import com.lumen.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cron 校验器测试。安全要求 #9 / #14。
 */
class CronValidatorTest {

    // ---------- validate: 合法 cron ----------

    @Test
    void validate_dailyAtMidnight_succeeds() {
        assertDoesNotThrow(() -> CronValidator.validate("0 0 0 * * *"));
    }

    @Test
    void validate_everyTwoHours_succeeds() {
        // 0 0 0/2 * * *  — 每 2 小时
        assertDoesNotThrow(() -> CronValidator.validate("0 0 0/2 * * *"));
    }

    @Test
    void validate_weeklyMonday_succeeds() {
        // 0 0 9 ? * MON — 每周一 9 点
        assertDoesNotThrow(() -> CronValidator.validate("0 0 9 ? * MON"));
    }

    // ---------- validate: 非法 cron ----------

    @Test
    void validate_invalidExpression_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> CronValidator.validate("not-a-cron"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_empty_throws400() {
        assertEquals(400, assertThrows(ServiceException.class,
            () -> CronValidator.validate("")).getCode());
        assertEquals(400, assertThrows(ServiceException.class,
            () -> CronValidator.validate(null)).getCode());
        assertEquals(400, assertThrows(ServiceException.class,
            () -> CronValidator.validate("   ")).getCode());
    }

    // ---------- validate: 频率 < 1h 拒绝 ----------

    @Test
    void validate_everyMinute_throws400() {
        // 0 * * * * * — 每分钟 (1 分钟 < 60 分钟)
        ServiceException ex = assertThrows(ServiceException.class,
            () -> CronValidator.validate("0 * * * * *"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("frequency too high"));
    }

    @Test
    void validate_everyFiveMinutes_throws400() {
        // 0 0/5 * * * * — 每 5 分钟
        ServiceException ex = assertThrows(ServiceException.class,
            () -> CronValidator.validate("0 0/5 * * * *"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void validate_everyHalfHour_throws400() {
        // 0 0/30 * * * * — 每 30 分钟
        ServiceException ex = assertThrows(ServiceException.class,
            () -> CronValidator.validate("0 0/30 * * * *"));
        assertEquals(400, ex.getCode());
    }

    // ---------- nextFireTime ----------

    @Test
    void nextFireTime_validCron_returnsFutureTime() {
        LocalDateTime next = CronValidator.nextFireTime("0 0 12 * * *");
        assertNotNull(next);
        // 应该是未来时间
        assertTrue(next.isAfter(LocalDateTime.now().minusSeconds(1)));
    }

    @Test
    void nextFireTime_invalidCron_throws400() {
        assertEquals(400, assertThrows(ServiceException.class,
            () -> CronValidator.nextFireTime("bogus")).getCode());
    }
}