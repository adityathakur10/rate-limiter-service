package com.ratelimiter.common.algorithm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Rate-limit algorithms a rule can use. The lowercase {@link #value()} is the contract in JSON
 * and (from step 3.1) in the database. A new algorithm starts with one line here.
 */
public enum AlgorithmType {
    FIXED_WINDOW("fixed_window");

    private final String value;

    AlgorithmType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AlgorithmType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown algorithm: " + value));
    }
}
