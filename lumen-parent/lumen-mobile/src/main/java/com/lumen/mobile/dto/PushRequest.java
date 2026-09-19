package com.lumen.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 服务端推送请求（管理员触发 — 站内通知或活动推送）。
 * userId 必填；pushToUser 必须有有效 token，否则 service 返回 400（安全要求 #11）。
 */
@Data
public class PushRequest {

    @NotBlank(message = "userId is required")
    private Long userId;

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "content is required")
    private String content;

    /** 客户端点击推送时跳转的 deep link（如 lumen://workflow/todo/123）。 */
    private String deepLink;
}
