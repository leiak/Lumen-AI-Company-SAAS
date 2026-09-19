package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 印章。seal_type: company=公章 / contract=合同章 / finance=财务章 / legal=法人章。
 * status: in_use=在用 / sealed=封存 / destroyed=已销毁。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_seal")
public class AstSeal extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String sealType;
    private Long keeperId;
    private String status;
}