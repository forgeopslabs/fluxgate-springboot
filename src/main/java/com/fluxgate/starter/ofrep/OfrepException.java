package com.fluxgate.starter.ofrep;

import com.fluxgate.starter.ErrorCode;

/**
 * A failed OFREP call, already mapped to an {@link ErrorCode}.
 */
public class OfrepException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int status;
    private final boolean retryable;

    /**
     * @param errorCode the mapped error code
     * @param message   human-readable detail
     * @param status    the HTTP status, or {@code -1} when there was no response
     * @param retryable whether the call may be retried
     */
    public OfrepException(ErrorCode errorCode, String message, int status, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.retryable = retryable;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
