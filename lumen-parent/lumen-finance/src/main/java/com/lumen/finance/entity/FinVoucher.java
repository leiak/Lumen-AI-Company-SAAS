package com.lumen.finance.entity;

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
 * 凭证。
 * status: draft/posted/reversed.
 * period: yyyy-MM.
 * reversedId: when this voucher is a reversal of another voucher, points to the
 * original fin_voucher.id (the original voucher records this id as its reversedId
 * too — i.e. bidirectional link).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher")
public class FinVoucher extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String voucherNo;
    /** yyyy-MM */
    private String period;
    private LocalDate voucherDate;
    private String summary;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    /** draft / posted / reversed */
    private String status;
    private LocalDateTime postedAt;
    private Long postedBy;
    private Long reversedId;
}
