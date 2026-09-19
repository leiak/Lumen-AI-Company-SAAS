package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 离职申请。
 * status: 0=已提交 1=审批中 2=已通过 3=已生效 4=已驳回 5=已撤销
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_resignation")
public class HrResignation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String reason;
    private LocalDateTime submitAt;
    private LocalDate effectiveAt;
    private Integer status;
    private Long tenantId;
}
