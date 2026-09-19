package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 资产调拨记录。status: pending=待审批 / approved=已批准 / rejected=已拒绝 / completed=已完成。
 * 状态机: pending → approved → completed；pending → rejected 终态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_transfer")
public class AstTransfer extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long assetId;
    private Long fromDeptId;
    private Long fromCustodianId;
    private Long toDeptId;
    private Long toCustodianId;
    private LocalDate transferDate;
    private String reason;
    private String status;
}