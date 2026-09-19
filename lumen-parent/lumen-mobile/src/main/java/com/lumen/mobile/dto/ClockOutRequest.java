package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 下班打卡请求（type 固定为 clock_out）。
 */
@Data
public class ClockOutRequest {

    @NotNull(message = "latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    private BigDecimal longitude;

    /** 反查地址（可选）。 */
    private String address;

    @NotBlank(message = "deviceId is required")
    private String deviceId;
}
