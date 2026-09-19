package com.lumen.file.exception;

import com.lumen.common.core.exception.ServiceException;

/**
 * File-domain exception. Delegates to {@link ServiceException} so the global
 * advice ({@code GlobalExceptionAdvice}) maps the code to an HTTP status when
 * the value is in the 4xx/5xx range.
 *
 * <p>This is a thin marker — we don't add behavior so callers can stay on
 * {@code ServiceException} for catching. Use this class for clarity at
 * throw-sites.</p>
 */
public class FileException extends ServiceException {

    private static final long serialVersionUID = 1L;

    public FileException(int code, String message) {
        super(code, message);
    }

    public FileException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
