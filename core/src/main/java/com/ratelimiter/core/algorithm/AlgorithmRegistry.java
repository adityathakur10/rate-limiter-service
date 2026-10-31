package com.ratelimiter.core.algorithm;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Finds the implementation for a rule's algorithm. Built from every {@link RateLimitAlgorithm} bean. */
@Component
public class AlgorithmRegistry {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmRegistry.class);
    private static final AlgorithmType DEFAULT = AlgorithmType.FIXED_WINDOW;

    private final Map<AlgorithmType, RateLimitAlgorithm> algorithms = new EnumMap<>(AlgorithmType.class);

    public AlgorithmRegistry(List<RateLimitAlgorithm> implementations) {
        for (RateLimitAlgorithm algorithm : implementations) {
            RateLimitAlgorithm previous = algorithms.put(algorithm.type(), algorithm);
            if (previous != null) {
                throw new IllegalStateException("Two implementations claim algorithm " + algorithm.type()
                        + ": " + previous.getClass().getName() + " and " + algorithm.getClass().getName());
            }
        }
        if (!algorithms.containsKey(DEFAULT)) {
            throw new IllegalStateException("No implementation for the default algorithm " + DEFAULT);
        }
    }

    /** Null means the default; a type with no implementation logs an ERROR and falls back to the default. */
    public RateLimitAlgorithm resolve(RuleDto rule) {
        AlgorithmType type = rule.algorithm();
        if (type == null) {
            return algorithms.get(DEFAULT);
        }
        RateLimitAlgorithm algorithm = algorithms.get(type);
        if (algorithm == null) {
            log.error("No implementation for algorithm '{}' on rule {}; falling back to {}",
                    type.value(), rule.id(), DEFAULT.value());
            return algorithms.get(DEFAULT);
        }
        return algorithm;
    }
}
