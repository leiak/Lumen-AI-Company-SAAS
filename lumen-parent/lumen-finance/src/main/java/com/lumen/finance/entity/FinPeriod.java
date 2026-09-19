package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会计期间。status: open/closed/locked.
 * Locked 期间不能再 reopen。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_period")
public class FinPeriod extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Integer year;
    private Integer month;
    /** open / closed / locked */
    private String status;
    private LocalDateTime closedAt;
}
