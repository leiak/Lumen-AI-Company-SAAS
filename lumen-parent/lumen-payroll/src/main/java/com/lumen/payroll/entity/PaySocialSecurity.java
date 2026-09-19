package com.lumen.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 社保。items: JSON {pension:..., medical:..., unemployment:..., housingFund:...}
 * status: calculated/declared/paid.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pay_social_security", autoResultMap = true)
public class PaySocialSecurity extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long employeeId;
    /** yyyy-MM */
    private String period;
    private BigDecimal baseAmount;
    private BigDecimal employeeAmount;
    private BigDecimal employerAmount;

    @TableField(value = "items", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> items;

    /** calculated / declared / paid */
    private String status;
}