package com.lumen.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资产分类（树形）。parent_id=0 表示根节点，path 用 "/" 分隔物化路径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_category")
public class AstCategory extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long parentId;
    private String code;
    private String name;
    private Integer level;
    private String path;
    private String depreciationMethodDefault;
    private Integer usefulLifeDefault;
}