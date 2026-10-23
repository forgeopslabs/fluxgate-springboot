package com.forgeopslabs.fluxgate.openfeature;

import com.forgeopslabs.fluxgate.ErrorCode;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;

/**
 * Maps FluxGate error codes to OpenFeature error codes and exceptions.
 * {@link ErrorCode#UNAUTHORIZED} (HTTP 401/403) has no OpenFeature equivalent
 * and maps to {@code GENERAL}; the message keeps the edge's detail.
 */
final class OpenFeatureErrors {

    private OpenFeatureErrors() {
    }

    static dev.openfeature.sdk.ErrorCode toErrorCode(ErrorCode code) {
        return switch (code) {
            case FLAG_NOT_FOUND -> dev.openfeature.sdk.ErrorCode.FLAG_NOT_FOUND;
            case TYPE_MISMATCH -> dev.openfeature.sdk.ErrorCode.TYPE_MISMATCH;
            case TARGETING_KEY_MISSING -> dev.openfeature.sdk.ErrorCode.TARGETING_KEY_MISSING;
            case INVALID_CONTEXT -> dev.openfeature.sdk.ErrorCode.INVALID_CONTEXT;
            case PROVIDER_NOT_READY -> dev.openfeature.sdk.ErrorCode.PROVIDER_NOT_READY;
            case UNAUTHORIZED, GENERAL -> dev.openfeature.sdk.ErrorCode.GENERAL;
        };
    }

    /**
     * @return the exception {@code initialize} throws for a failed bulk
     *         evaluation; the SDK puts the provider in {@code ERROR} and emits
     *         {@code PROVIDER_ERROR} with its message
     */
    static OpenFeatureError initializationFailure(ErrorCode code, String message) {
        String detail = "FluxGate initialization failed (" + code + "): " + message;
        return switch (code) {
            case TARGETING_KEY_MISSING -> new TargetingKeyMissingError(detail);
            case INVALID_CONTEXT -> new InvalidContextError(detail);
            case PROVIDER_NOT_READY -> new ProviderNotReadyError(detail);
            default -> new GeneralError(detail);
        };
    }
}
