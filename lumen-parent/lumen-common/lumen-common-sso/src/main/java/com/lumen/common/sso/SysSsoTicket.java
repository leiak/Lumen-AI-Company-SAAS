package com.lumen.common.sso;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 票据表实体。
 * <p>
 * 不继承 {@code BaseEntity}：本表的自增主键为 {@code id}，而 {@code BaseEntity} 默认按
 * {@code createBy/createTime/updateBy/updateTime/deleted} 提供自动填充——这里我们保留
 * 这些字段但通过 MyBatis-Plus 的 {@code @TableField(fill = ...)} 让 {@code FieldFillHandler}
 * 接管自动填充；{@code deleted} 用 {@code @TableLogic} 启用逻辑删除。
 * </p>
 * <p>
 * 票据一次性消费：原子 UPDATE 由 {@link TicketMapper#consumeAtomically} 完成，查询路径仅用于诊断。
 * </p>
 */
@Data
@TableName("sys_sso_ticket")
public class SysSsoTicket {

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

    @TableField(value = "create_by", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long createBy;

    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_by", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}