package com.lumen.common.core.constant;

/**
 * 通用常量
 */
public interface CommonConstants {

    int SUCCESS_CODE = 200;
    int FAIL_CODE = 500;
    int UNAUTHORIZED = 401;
    int FORBIDDEN = 403;
    int NOT_FOUND = 404;

    Long DEFAULT_TENANT_ID = 1L;
    String DEFAULT_TENANT_CODE = "default";

    String HEADER_TENANT_ID = "X-Tenant-Id";
    String HEADER_TRACE_ID = "X-Trace-Id";
    String HEADER_AUTHORIZATION = "Authorization";

    String TOKEN_PREFIX = "Bearer ";

    int NOT_DELETED = 0;
    int DELETED = 1;
}