package com.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_session")
public class SysUserSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long userId;
    private Long tenantId;
    private String refreshToken;
    private String ip;
    private String userAgent;
    private String device;
    private LocalDateTime loginAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime expireAt;
    private Integer status;
    private LocalDateTime logoutAt;
    private Long updateBy;
    private LocalDateTime updateTime;
}