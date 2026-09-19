package com.lumen.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义。{@code bpmnXml} 为 LONGTEXT，存储 BPMN 2.0 XML。
 * status: 0=草稿 1=已发布 2=已下线
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_definition")
public class WfDefinition extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String defKey;
    private String name;
    private Integer version;
    private String category;
    private String bpmnXml;
    private Integer status;
}