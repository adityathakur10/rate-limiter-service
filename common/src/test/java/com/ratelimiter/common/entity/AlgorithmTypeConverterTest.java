package com.ratelimiter.common.entity;

import com.ratelimiter.common.algorithm.AlgorithmType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlgorithmTypeConverterTest {

    private final AlgorithmTypeConverter converter = new AlgorithmTypeConverter();

    @Test
    void storesTheLowercaseValueNotTheConstantName() {
        assertThat(converter.convertToDatabaseColumn(AlgorithmType.FIXED_WINDOW)).isEqualTo("fixed_window");
    }

    @Test
    void readsTheLowercaseValueBack() {
        assertThat(converter.convertToEntityAttribute("fixed_window")).isEqualTo(AlgorithmType.FIXED_WINDOW);
    }

    @Test
    void everyTypeSurvivesARoundTrip() {
        for (AlgorithmType type : AlgorithmType.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(type))).isEqualTo(type);
        }
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void anUnknownStoredValueIsRejected() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
