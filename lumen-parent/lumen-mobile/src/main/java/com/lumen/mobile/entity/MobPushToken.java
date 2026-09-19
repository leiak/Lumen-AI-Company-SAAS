package com.lumen.mobile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.crypto.EncryptedStringTypeHandler;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 移动端推送 Token。
 *
 * <p>{@code pushTokenEnc} 与 {@code tokenHash} 关系：</p>
 * <ul>
 *   <li>{@code pushTokenEnc} 存 AES-256-GCM 加密后的推送 token 原文（FCM/APNs/HMS 颁发的设备 token）。</li>
 *   <li>{@code tokenHash} 存 {@code md5(token)} 十六进制，作为查询索引 — 避免密文比对无意义，
 *       也能快速定位同一 token 是否已注册（register 去重）。</li>
 * </ul>
 *
 * <p>UNIQUE(platform, device_id, deleted) — 同一平台同一设备只保留一条 active 记录；
 * register 时先 deactivate 旧记录再插入新记录，确保 9) active push token per user+platform 唯一。</p>
 *
 * <p>status: active / inactive。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mob_push_token", autoResultMap = true)
public class MobPushToken extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;

    /** android / ios / harmony。 */
    private String platform;

    /** 设备唯一标识（Android: ANDROID_ID；iOS: identifierForVendor；Harmony: ODID）。 */
    private String deviceId;

    /**
     * 推送 token AES-256-GCM 加密值（Base64 envelope）。
     * 严禁明文落库：第三方推送通道 token 一旦泄露可被滥用推送广告/钓鱼。
     */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String pushTokenEnc;

    /** md5(token) 十六进制 32 字符，用作查询索引（不存明文）。 */
    private String tokenHash;

    /** 客户端 App 版本（冗余字段，便于排查推送兼容问题）。 */
    private String appVersion;

    /** 设备型号（如 "Pixel 7" / "iPhone 15"）。 */
    private String deviceModel;

    /** 操作系统版本（如 "Android 14" / "iOS 17.2"）。 */
    private String osVersion;

    /** active / inactive。 */
    private String status;

    private LocalDateTime lastActiveAt;
}
