package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 调岗记录（审计轨迹）。
 * status: 0=待审批 1=已通过 2=已驳回 3=已生效
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_transfer")
public class HrTransfer extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long fromDeptId;
    private Long toDeptId;
    private Long fromPostId;
    private Long toPostId;
    private LocalDate effectiveAt;
    private Integer status;
    private Long tenantId;
}
