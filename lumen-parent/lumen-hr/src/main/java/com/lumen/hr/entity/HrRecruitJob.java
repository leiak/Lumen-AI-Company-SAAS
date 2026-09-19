package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 招聘需求 JD。
 * status: 0=草稿 1=已发布 2=已关闭 3=已招满
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_job")
public class HrRecruitJob extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Long deptId;
    private Long postId;
    private Integer headcount;
    private Integer status;
    private LocalDateTime publishedAt;
    private Long tenantId;
}
