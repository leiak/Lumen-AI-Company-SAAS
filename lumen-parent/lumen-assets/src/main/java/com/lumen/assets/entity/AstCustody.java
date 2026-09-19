package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资产领用/归还记录。status: active=在持 / returned=已归还。
 * 同一资产同时只能有一条 status='active' 的记录（应用层 + 唯一约束双重保证）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_custody")
public class AstCustody extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long assetId;
    private Long custodianId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime returnAt;
    private String status;
}