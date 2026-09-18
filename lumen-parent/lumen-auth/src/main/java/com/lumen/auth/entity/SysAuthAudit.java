package com.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_auth_audit")
public class SysAuthAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    private Long tenantId;
    private String action;
    private Integer status;
    private String ip;
    private String userAgent;
    private String detail;
    private LocalDateTime auditAt;
}