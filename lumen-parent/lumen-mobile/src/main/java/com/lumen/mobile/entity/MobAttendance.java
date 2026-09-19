package com.lumen.mobile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumen.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 移动端考勤打卡记录。
 *
 * <p>UNIQUE(user_id, type, DATE(clocked_at), deleted) — 一天一次打卡（同 type）。
 * service 层在 INSERT 前 selectCount 预校验，避免依赖 DB UNIQUE 报错。</p>
 *
 * <p>type: clock_in / clock_out。</p>
 * <p>status: normal / late / early / absent。</p>
 * <p>distanceFromOfficeM: 与办公室直线距离（米）；超阈值时 service 拒绝写入（TODO P5 真实范围）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mob_attendance")
public class MobAttendance extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;

    /** clock_in / clock_out。 */
    private String type;

    /** 打卡纬度（WGS-84）。 */
    private BigDecimal latitude;

    /** 打卡经度（WGS-84）。 */
    private BigDecimal longitude;

    /** 反查的地址字符串（来自地图 API）。 */
    private String address;

    /** 打卡照片 URL（VARCHAR(512)，超长拒绝）。 */
    private String photoUrl;

    /** 设备 ID（冗余字段，便于事后审计同一设备多账号）。 */
    private String deviceId;

    /** 与办公室直线距离（米）；NULL 表示 GPS 校验未启用。 */
    private Integer distanceFromOfficeM;

    /** normal / late / early / absent。 */
    private String status;

    private LocalDateTime clockedAt;
}
