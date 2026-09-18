package com.lumen.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long deptId;
    private Long tenantId;
    private Long parentId;
    /** Comma-separated ancestor dept_ids, e.g. "0,100,101" */
    private String ancestors;
    private String deptName;
    private String deptCategory;
    private Integer orderNum;
    private String leader;
    private String leaderName;
    private String phone;
    private String email;
    private String status;
    private Integer isBuiltin;
    private String remark;
}