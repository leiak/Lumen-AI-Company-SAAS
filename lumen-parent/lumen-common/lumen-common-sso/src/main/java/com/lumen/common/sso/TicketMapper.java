package com.lumen.common.sso;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * {@code sys_sso_ticket} 表的 MyBatis-Plus Mapper。
 * <p>
 * 仅暴露一次性消费所需的原子 UPDATE 方法；新增/查询通过继承自 {@link BaseMapper} 的方法完成。
 * </p>
 * <p>
 * 该 Mapper 上的所有方法都必须跳过多租户拦截器：{@code sys_sso_ticket} 是跨租户资源——
 * 票据签发时的租户和消费时的租户可能不同（上游认证 → 下游应用）。{@code IGNORE_TABLES}
 * 白名单也会命中，但方法级注解是纵深防御的一层。
 * </p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface TicketMapper extends BaseMapper<SysSsoTicket> {

    /**
     * 原子消费票据：仅当票据存在、未消费、未过期、未被逻辑删除时，将 consumed_at 标记为 NOW()。
     *
     * @param ticket 票据字符串
     * @param appId  应用标识（必须与签发时一致）
     * @return 受影响行数；0 表示票据无效、已消费或已过期
     */
    @Update("UPDATE sys_sso_ticket " +
        "SET consumed_at = NOW() " +
        "WHERE ticket = #{ticket} " +
        "  AND app_id = #{appId} " +
        "  AND consumed_at IS NULL " +
        "  AND expires_at > NOW() " +
        "  AND deleted = 0")
    int consumeAtomically(@Param("ticket") String ticket, @Param("appId") String appId);
}