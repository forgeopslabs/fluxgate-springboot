package com.forgeopslabs.fluxgate;

/**
 * Why a flag evaluation fell back to the caller's default value.
 */
public enum ErrorCode {

    /** The edge does not know the flag. */
    FLAG_NOT_FOUND,

    /** The flag value does not match the requested Java type. */
    TYPE_MISMATCH,

    /** The evaluation context has no targeting key. */
    TARGETING_KEY_MISSING,

    /** The evaluation context was rejected or could not be serialized. */
    INVALID_CONTEXT,

    /** The edge is up but not ready to evaluate (HTTP 503). */
    PROVIDER_NOT_READY,

    /** The SDK key is missing, invalid, disabled or not allowed (HTTP 401/403). */
    UNAUTHORIZED,

    /** Any other failure: transport errors, timeouts, 5xx, malformed responses. */
    GENERAL;

    /**
     * Maps an OFREP {@code errorCode} string to an error code.
     *
     * @param wireCode the error code sent by the edge, may be {@code null}
     * @param fallback the code to use when the wire code is absent or unknown
     * @return the matching error code or {@code fallback}
     */
    public static ErrorCode fromWire(String wireCode, ErrorCode fallback) {
        if (wireCode == null) {
            return fallback;
        }
        for (ErrorCode code : values()) {
            if (code.name().equals(wireCode)) {
                return code;
            }
        }
        return fallback;
    }
}
