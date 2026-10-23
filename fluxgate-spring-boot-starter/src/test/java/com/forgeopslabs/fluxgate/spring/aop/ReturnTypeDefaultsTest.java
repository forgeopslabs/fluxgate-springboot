package com.forgeopslabs.fluxgate.spring.aop;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnTypeDefaultsTest {

    static Stream<Arguments> defaults() {
        return Stream.of(
                Arguments.of(boolean.class, false),
                Arguments.of(char.class, '\0'),
                Arguments.of(byte.class, (byte) 0),
                Arguments.of(short.class, (short) 0),
                Arguments.of(int.class, 0),
                Arguments.of(long.class, 0L),
                Arguments.of(float.class, 0f),
                Arguments.of(double.class, 0d),
                Arguments.of(Optional.class, Optional.empty()),
                Arguments.of(void.class, null),
                Arguments.of(Boolean.class, null),
                Arguments.of(String.class, null),
                Arguments.of(List.class, null));
    }

    @ParameterizedTest
    @MethodSource("defaults")
    void defaultFor(Class<?> type, Object expected) {
        assertThat(ReturnTypeDefaults.defaultFor(type)).isEqualTo(expected);
    }
}
