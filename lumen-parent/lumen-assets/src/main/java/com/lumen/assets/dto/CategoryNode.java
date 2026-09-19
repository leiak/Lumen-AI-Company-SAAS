package com.lumen.assets.dto;

import com.lumen.assets.entity.AstCategory;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 资产分类树节点（前端展示用）。
 */
@Data
public class CategoryNode {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer level;
    private String depreciationMethodDefault;
    private Integer usefulLifeDefault;
    private List<CategoryNode> children = new ArrayList<>();

    public static CategoryNode from(AstCategory c) {
        CategoryNode n = new CategoryNode();
        n.id = c.getId();
        n.parentId = c.getParentId();
        n.code = c.getCode();
        n.name = c.getName();
        n.level = c.getLevel();
        n.depreciationMethodDefault = c.getDepreciationMethodDefault();
        n.usefulLifeDefault = c.getUsefulLifeDefault();
        return n;
    }
}