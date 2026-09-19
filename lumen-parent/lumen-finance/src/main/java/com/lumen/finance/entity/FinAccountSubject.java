package com.lumen.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会计科目（树形）。type: asset/liability/equity/income/expense.
 * balanceDirection: debit/credit.
 * path: materialised path, e.g. "/1/3/12/".
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_account_subject")
public class FinAccountSubject extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long parentId;
    private String code;
    private String name;
    private Integer level;
    private String type;
    private String balanceDirection;
    /** Materialised path e.g. "0,1,3,12". Used for subtree queries. */
    private String path;
    private Integer status;
}
