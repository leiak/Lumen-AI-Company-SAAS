package com.lumen.mobile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 协作平台应用配置（钉钉 / 企微 / 飞书）。
 *
 * <p>{@code appSecretEnc} 与 {@code webhookUrlEnc} 通过 {@link EncryptedStringTypeHandler}
 * 字段级加密（安全要求 #4）。</p>
 *
 * <p>{@code config} JSON（{@link JacksonTypeHandler}）存非敏感扩展配置：
 * 例如钉钉机器人加签密钥（部分场景），或企微可见范围配置。生产环境
 * 应将真敏感字段迁出至 Nacos 加密配置。</p>
 *
 * <p>code 在 (tenant_id, deleted) 内唯一。platform: dingtalk / wechatwork / feishu。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "int_app_config", autoResultMap = true)
public class IntegrationAppConfig extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 业务 code（如 "dingtalk-main"），租户内唯一。 */
    private String code;

    /** dingtalk / wechatwork / feishu。 */
    private String platform;

    /** 平台分配的应用 ID（明文，无敏感信息）。 */
    private String appId;

    /** App Secret（AES-GCM 加密）。 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String appSecretEnc;

    /** AgentID（钉钉/企微特有；飞书可空）。 */
    private String agentId;

    /** OAuth2 / SSO 回调地址。 */
    private String redirectUri;

    /** 机器人/事件回调 Webhook URL（加密）。 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String webhookUrlEnc;

    /** 0=禁用 1=启用。 */
    private Integer enabled;

    /** 扩展 JSON 配置（JacksonTypeHandler）。 */
    @TableField(value = "config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;
}
