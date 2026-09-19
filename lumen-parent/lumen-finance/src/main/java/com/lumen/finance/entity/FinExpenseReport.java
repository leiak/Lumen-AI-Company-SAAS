package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报销单。
 * items: JSON array of {subjectId, amount, summary, ...} via {@link JacksonTypeHandler}.
 * status: draft/submitted/approved/rejected/paid.
 * workflowInstanceId: id from lumen-workflow (set on submit).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "fin_expense_report", autoResultMap = true)
public class FinExpenseReport extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long applicantId;
    private Long departmentId;
    private BigDecimal totalAmount;

    @TableField(value = "items", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> items;

    /** draft / submitted / approved / rejected / paid */
    private String status;
    private Long workflowInstanceId;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
}
