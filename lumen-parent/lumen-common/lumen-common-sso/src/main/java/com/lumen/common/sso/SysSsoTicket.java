package com.lumen.common.sso;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * SSO 票据表实体。
 * <p>
 * 复用 {@link BaseEntity} 以复用 {@code createBy/createTime/updateBy/updateTime/deleted}
 * 自动填充与逻辑删除约定；本表的自增主键仍由 {@code id} 持有。
 * </p>
 * <p>
 * 票据一次性消费：原子 UPDATE 由 {@link TicketMapper#consumeAtomically} 完成，查询路径仅用于诊断。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_sso_ticket")
public class SysSsoTicket extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 票据字符串（Base64 URL，32 随机字节）。 */
    private String ticket;

    /** 票据所属用户 ID。 */
    private Long userId;

    /** 票据所属租户 ID。 */
    private Long tenantId;

    /** 目标应用标识（与消费时 appId 必须一致）。 */
    private String appId;

    /** 票据到期时间。 */
    private LocalDateTime expiresAt;

    /** 票据消费时间，NULL 表示未消费。 */
    private LocalDateTime consumedAt;
}