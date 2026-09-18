package com.lumen.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.crypto.Encrypted;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_employee")
public class SysEmployee extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long employeeId;
    private Long tenantId;
    private Long userId;
    private String employeeNo;
    private String name;
    private String namePinyin;
    private String gender;
    private String mobileEnc;
    private String emailEnc;
    /**
     * 身份证号（加密存储）。
     * 列名通过 MyBatis-Plus 下划线-驼峰映射自动解析为 {@code id_card_enc}。
     * 类型处理器在写入时加密、读取时解密；{@link Encrypted} 仅作文档标记。
     */
    @Encrypted
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String idCardEnc;
    private LocalDate birthDate;
    private LocalDate hireDate;
    private LocalDate leaveDate;
    private Long deptId;
    private Long postId;
    private Long directLeaderId;
    private String employeeType;
    private String employmentStatus;
    private Integer isBuiltin;
    private String remark;
}