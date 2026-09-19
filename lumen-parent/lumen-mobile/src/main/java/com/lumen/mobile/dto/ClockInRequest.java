package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 上班打卡请求（type 固定为 clock_in）。
 * deviceId 用于审计（同一设备多账号告警）。
 * photoUrl 限制 VARCHAR(512)，超长由 service 拒绝（安全要求 #16）。
 */
@Data
public class ClockInRequest {

    @NotNull(message = "latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    private BigDecimal longitude;

    /** 反查地址（可选）。 */
    private String address;

    /** 打卡照片 URL（VARCHAR(512)）。 */
    private String photoUrl;

    @NotBlank(message = "deviceId is required")
    private String deviceId;
}
