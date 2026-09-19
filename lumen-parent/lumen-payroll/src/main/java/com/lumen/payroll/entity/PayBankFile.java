package com.lumen.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 银行报盘文件。bankCode: ccb/icbc/cmb/etc.
 * status: generated/sent/confirmed/failed. status=generated 后内容冻结 (安全要求 #13).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_bank_file")
public class PayBankFile extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** yyyy-MM */
    private String period;
    /** ccb / icbc / cmb / abc / boc / etc */
    private String bankCode;
    private String filePath;
    private String fileMd5;
    private Integer employeeCount;
    private BigDecimal totalAmount;
    /** generated / sent / confirmed / failed */
    private String status;
    private LocalDateTime generatedAt;
    private LocalDateTime sentAt;
}