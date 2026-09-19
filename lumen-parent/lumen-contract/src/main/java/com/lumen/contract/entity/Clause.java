package com.lumen.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同条款（关联合同实例）。
 * source ENUM: template/manual
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ctr_clause")
public class Clause extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private String clauseNo;
    private String title;
    private String content;
    private Integer orderNum;
    private String source;
    private Long tenantId;
}
