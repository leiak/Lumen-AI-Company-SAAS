package com.lumen.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工档案。
 * status: 0=在职 1=试用期 2=离职 3=停薪留职
 *
 * <p>{@code idCardEnc}/{@code mobileEnc} 加密存储；{@link EncryptedStringTypeHandler}
 * 按字段级装配（不注册全局 TypeHandler，避免 P3 联调中破坏其他 String 列的问题）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_employee")
public class HrEmployee extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 sys_user.user_id；可为 null（待入职）。 */
    private Long userId;

    /** 业务编号，租户内唯一。 */
    private String code;

    /** 姓名。 */
    private String name;

    /** 身份证号（加密）。 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String idCardEnc;

    /** 手机号（加密）。 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String mobileEnc;

    /** 入职日期。 */
    private LocalDate hireDate;

    private Long deptId;
    private Long postId;
    private Long levelId;

    /** 0=在职 1=试用期 2=离职 3=停薪留职 */
    private Integer status;

    private Long tenantId;
}
