package com.lumen.message.exception;

import com.lumen.common.core.exception.ServiceException;

/**
 * Message-domain marker exception. Delegates to {@link ServiceException} so the
 * global advice ({@code GlobalExceptionAdvice}) maps the code to an HTTP status.
 *
 * <p>Use for clarity at throw-sites — behavior is identical to {@code ServiceException}.</p>
 */
public class MessageException extends ServiceException {

    private static final long serialVersionUID = 1L;

    public MessageException(int code, String message) {
        super(code, message);
    }

    public MessageException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }
}