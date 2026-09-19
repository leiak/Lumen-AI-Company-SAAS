package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer 记录。
 * status: 0=草稿 1=已发送 2=候选人接受 3=候选人拒绝 4=已撤回
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_offer")
public class HrOffer extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private BigDecimal salary;
    private LocalDate startDate;
    private Integer status;
    private LocalDateTime sentAt;
    private Long tenantId;
}
