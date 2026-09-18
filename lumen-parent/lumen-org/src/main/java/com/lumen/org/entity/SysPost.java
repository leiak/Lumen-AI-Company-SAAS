package com.lumen.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPost extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long postId;
    private Long tenantId;
    private String postCode;
    private String postName;
    private Integer postSort;
    private String status;
    private String remark;
}