package com.lumen.message.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.message.entity.MsgNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MsgNotificationMapper extends BaseMapper<MsgNotification> {

    /**
     * Page notifications for a recipient. Tenant filter is enforced via the
     * TenantLineInnerInterceptor (msg_notification has a tenant_id column).
     *
     * @param tenantId       caller's tenant (defense in depth — interceptor also pins it)
     * @param recipientUserId recipient user id
     * @param status         optional status filter (null = all)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM msg_notification "
        + "WHERE tenant_id = #{tenantId} AND recipient_user_id = #{recipientUserId} "
        + "AND deleted = 0 "
        + "<if test='status != null'> AND status = #{status} </if>"
        + "ORDER BY id DESC")
    IPage<MsgNotification> listByRecipient(Page<MsgNotification> page,
                                           @Param("tenantId") Long tenantId,
                                           @Param("recipientUserId") Long recipientUserId,
                                           @Param("status") Integer status);

    /**
     * Atomic batch mark-read — only marks rows the user owns in their tenant.
     * Returns affected row count.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE msg_notification "
        + "SET status = 3, read_time = NOW(), update_time = NOW() "
        + "WHERE tenant_id = #{tenantId} AND recipient_user_id = #{userId} "
        + "AND id IN (<foreach collection='ids' item='id' separator=','>#{id}</foreach>) "
        + "AND deleted = 0")
    int markReadBatch(@Param("tenantId") Long tenantId,
                      @Param("userId") Long userId,
                      @Param("ids") List<Long> ids);

    default LambdaQueryWrapper<MsgNotification> tenantWrapper() {
        return new LambdaQueryWrapper<>();
    }
}