package com.lumen.common.core.domain;

import com.lumen.common.core.constant.CommonConstants;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String msg;
    private T data;

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(CommonConstants.SUCCESS_CODE, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(CommonConstants.SUCCESS_CODE, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(CommonConstants.SUCCESS_CODE, msg, data);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(CommonConstants.FAIL_CODE, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(int code, String msg, T data) {
        return new R<>(code, msg, data);
    }
}