package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 凭证明细。一行借贷方向之一：要么 debitAmount>0，要么 creditAmount>0，
 * 二者 XOR（在 service 层校验）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher_entry")
public class FinVoucherEntry extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long voucherId;
    private Long subjectId;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String summary;
}
