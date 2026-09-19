package com.lumen.mobile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 移动 App 版本。
 * platform: android / ios / harmony。
 * status: draft / released / deprecated。
 * forceUpdate: 0=不强制 1=强制。
 *
 * <p>UNIQUE(platform, version, deleted) — 同一平台同一版本号只能有一条非删除记录。
 * 注意 buildNumber 仅作展示，不参与唯一约束（同一 version 可多次构建）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mob_app_version")
public class MobAppVersion extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** android / ios / harmony */
    private String platform;

    /** 语义版本号，如 1.0.0。 */
    private String version;

    /** 构建号（递增整数；用于 App Store / Play Store 比对）。 */
    private Integer buildNumber;

    /** 0=不强制 1=强制升级。 */
    private Integer forceUpdate;

    /** 最低支持版本；低于此版本强制升级。 */
    private String minSupportedVersion;

    /** APK / IPA 下载链接（生产必须签名；TODO P5）。 */
    private String downloadUrl;

    /** 发布说明（TEXT）。 */
    private String releaseNotes;

    /** draft / released / deprecated。 */
    private String status;

    private LocalDateTime releasedAt;
}
