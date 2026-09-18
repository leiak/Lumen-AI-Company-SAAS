package com.lumen.common.core.exception;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class ServiceException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int code;

    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}