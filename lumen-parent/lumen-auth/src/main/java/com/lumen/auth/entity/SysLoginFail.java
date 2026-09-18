package com.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_fail")
public class SysLoginFail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userName;
    private Long tenantId;
    private String ip;
    private String userAgent;
    private String failReason;
    private LocalDateTime failAt;
}