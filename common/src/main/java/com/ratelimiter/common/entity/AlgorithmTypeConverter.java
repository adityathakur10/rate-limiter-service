package com.ratelimiter.common.entity;

import com.ratelimiter.common.algorithm.AlgorithmType;
import jakarta.persistence.AttributeConverter;

/** Stores the lowercase contract value (fixed_window), not the enum constant name. */
public class AlgorithmTypeConverter implements AttributeConverter<AlgorithmType, String> {

    @Override
    public String convertToDatabaseColumn(AlgorithmType type) {
        return type == null ? null : type.value();
    }

    @Override
    public AlgorithmType convertToEntityAttribute(String value) {
        return value == null ? null : AlgorithmType.fromValue(value);
    }
}
