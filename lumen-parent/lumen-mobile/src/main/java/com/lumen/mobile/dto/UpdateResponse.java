package com.lumen.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App 升级检测返回（公开接口）。
 * updateAvailable: 是否需要升级。
 * forceUpdate: 强制升级标记（前端无需弹"暂不更新"按钮）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResponse {
    private boolean updateAvailable;
    private boolean forceUpdate;
    private String latestVersion;
    private Integer latestBuildNumber;
    private String downloadUrl;
    private String releaseNotes;
}
