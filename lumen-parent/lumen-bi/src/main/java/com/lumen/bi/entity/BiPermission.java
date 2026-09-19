package com.lumen.bi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * BI 资源权限。resource_type: dashboard/metric/report/dataset。
 * principal_type: user/role/dept。
 * permission: view/edit/admin。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_permission")
public class BiPermission extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** dashboard/metric/report/dataset */
    private String resourceType;
    private Long resourceId;
    /** user/role/dept */
    private String principalType;
    private Long principalId;
    /** view/edit/admin */
    private String permission;
    private LocalDateTime grantedAt;
    private Long grantedBy;
}