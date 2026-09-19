package com.lumen.mobile.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.mobile.entity.IntegrationEventLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 集成事件日志 mapper（仅追加，安全要求 #13）。
 * service 层不提供 update / delete API；processEvent 仅更新 processed/processedAt 状态位。
 */
@Mapper
public interface IntegrationEventLogMapper extends BaseMapper<IntegrationEventLog> {

    /**
     * 平台下全部事件（按 received_at DESC）。
     */
    default List<IntegrationEventLog> findByPlatform(String platform) {
        return selectList(new LambdaQueryWrapper<IntegrationEventLog>()
            .eq(IntegrationEventLog::getPlatform, platform)
            .eq(IntegrationEventLog::getDeleted, 0)
            .orderByDesc(IntegrationEventLog::getId));
    }

    /**
     * 按 eventType 查询（运营分析用）。
     */
    default List<IntegrationEventLog> findByEventType(String eventType) {
        return selectList(new LambdaQueryWrapper<IntegrationEventLog>()
            .eq(IntegrationEventLog::getEventType, eventType)
            .eq(IntegrationEventLog::getDeleted, 0)
            .orderByDesc(IntegrationEventLog::getId));
    }

    /**
     * 取前 N 条待处理事件（processed=0），供后台 worker 批量处理。
     */
    @Select("SELECT * FROM int_event_log WHERE processed = 0 AND deleted = 0 "
        + "ORDER BY id ASC LIMIT #{limit}")
    List<IntegrationEventLog> findUnprocessed(@Param("limit") int limit);

    /**
     * 按 (platform, sourceId) 唯一查询 — 用于防重放校验（UNIQUE 在 DB 层兜底）。
     */
    default IntegrationEventLog findBySource(String platform, String sourceId) {
        return selectOne(new LambdaQueryWrapper<IntegrationEventLog>()
            .eq(IntegrationEventLog::getPlatform, platform)
            .eq(IntegrationEventLog::getSourceId, sourceId)
            .eq(IntegrationEventLog::getDeleted, 0));
    }
}
