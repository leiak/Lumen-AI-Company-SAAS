package com.lumen.message.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.message.entity.MsgSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MsgSubscriptionMapper extends BaseMapper<MsgSubscription> {

    /**
     * Enabled subscriptions for a user + event. Bypasses tenant interceptor
     * because service-layer already pins tenant.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM msg_subscription "
        + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
        + "AND event_type = #{eventType} AND enabled = 1 AND deleted = 0")
    List<MsgSubscription> findEnabledForUser(@Param("tenantId") Long tenantId,
                                              @Param("userId") Long userId,
                                              @Param("eventType") String eventType);

    default LambdaQueryWrapper<MsgSubscription> tenantWrapper() {
        return new LambdaQueryWrapper<>();
    }
}