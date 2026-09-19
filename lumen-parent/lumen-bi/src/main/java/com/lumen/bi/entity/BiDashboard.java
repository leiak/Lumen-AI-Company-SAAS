package com.lumen.bi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 看板。status: draft/published/archived。
 * layout JSON: 看板布局配置。
 * publishedAt: 发布时间戳。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "bi_dashboard", autoResultMap = true)
public class BiDashboard extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    /** JSON: 看板布局 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> layout;
    /** draft/published/archived */
    private String status;
    private LocalDateTime publishedAt;
}