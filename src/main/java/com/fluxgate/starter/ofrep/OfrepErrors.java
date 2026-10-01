package com.fluxgate.starter.ofrep;

import com.fluxgate.starter.ErrorCode;

/**
 * Maps non-success edge responses to {@link OfrepException}s.
 */
final class OfrepErrors {

    private OfrepErrors() {
    }

    /**
     * @param status   the HTTP status
     * @param wireCode the {@code errorCode} from the body, may be {@code null}
     * @param details  the {@code errorDetails} (or raw body), may be
     *                 {@code null}
     * @return the mapped exception; only 502, 503 and 504 are retryable
     */
    static OfrepException fromResponse(int status, String wireCode, String details) {
        String message = details == null || details.isBlank()
                ? "Edge returned HTTP " + status
                : "Edge returned HTTP " + status + ": " + details;
        return new OfrepException(codeFor(status, wireCode), message, status, isRetryable(status));
    }

    static OfrepException malformed(String what, Exception cause) {
        return new OfrepException(ErrorCode.GENERAL, "Malformed " + what + " from edge: " + cause.getMessage(),
                200, false);
    }

    private static ErrorCode codeFor(int status, String wireCode) {
        return switch (status) {
            case 400 -> ErrorCode.fromWire(wireCode, ErrorCode.INVALID_CONTEXT);
            case 401, 403 -> ErrorCode.UNAUTHORIZED;
            case 404 -> ErrorCode.FLAG_NOT_FOUND;
            case 503 -> ErrorCode.PROVIDER_NOT_READY;
            default -> ErrorCode.GENERAL;
        };
    }

    private static boolean isRetryable(int status) {
        return status == 502 || status == 503 || status == 504;
    }
}
