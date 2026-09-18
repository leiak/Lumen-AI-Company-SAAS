package com.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    private Long tenantId;
    private Long deptId;
    private String userName;
    private String nickName;
    private String email;
    private String phonenumber;
    private String sex;
    private String avatar;
    private String password;
    /** '0'=active '1'=disabled */
    private String status;
    private Integer dataScope;
    private String mfaSecret;
    private Integer mfaEnabled;
    /** Admin-driven "force MFA on next login" flag. See V1.5.0__init_mfa.sql. */
    private Integer mfaRequired;
    private LocalDateTime pwdExpireAt;
    private String pwdHistory;
    private String idCardEnc;
    private String mobileEnc;
    private String emailEnc;
    private String bankCardEnc;
    private LocalDateTime lastPwdChange;
    private Integer failCount;
    private LocalDateTime lockUntil;
    private String loginIp;
    private LocalDateTime loginDate;
    private String remark;
}