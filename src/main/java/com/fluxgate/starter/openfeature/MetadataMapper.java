package com.fluxgate.starter.openfeature;

import dev.openfeature.sdk.ImmutableMetadata;

import java.util.Map;

/**
 * Converts OFREP flag metadata to OpenFeature {@link ImmutableMetadata}, which
 * holds only strings, booleans and numbers. Nested objects, arrays and
 * {@code null}s are dropped. Numbers keep Jackson's types ({@code Integer},
 * {@code Long}); decimals and integers beyond the {@code long} range become
 * {@code Double}.
 */
final class MetadataMapper {

    private MetadataMapper() {
    }

    static ImmutableMetadata toMetadata(Map<String, Object> metadata) {
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
        metadata.forEach((key, value) -> add(builder, key, value));
        return builder.build();
    }

    private static void add(ImmutableMetadata.ImmutableMetadataBuilder builder, String key, Object value) {
        if (value instanceof String text) {
            builder.addString(key, text);
        } else if (value instanceof Boolean flag) {
            builder.addBoolean(key, flag);
        } else if (value instanceof Integer number) {
            builder.addInteger(key, number);
        } else if (value instanceof Long number) {
            builder.addLong(key, number);
        } else if (value instanceof Number number) {
            // Double, or a BigInteger beyond the long range.
            builder.addDouble(key, number.doubleValue());
        }
    }
}
