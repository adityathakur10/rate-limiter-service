package com.ratelimiter.core.service;

import com.ratelimiter.common.dto.RuleDto;

import java.util.List;
import java.util.Optional;

/**
 * Picks the one rule that applies to a request: the most specific match wins.
 * <ol>
 *   <li>exact endpoint and exact method</li>
 *   <li>exact endpoint, any method ({@code *})</li>
 *   <li>any endpoint ({@code *}), exact method</li>
 *   <li>{@code *} and {@code *}</li>
 * </ol>
 * The endpoint is compared as exact text; the method ignores case. No match gives an empty result,
 * which the caller treats as "allow".
 */
public final class RuleMatcher {

    private static final String WILDCARD = "*";

    private RuleMatcher() {
    }

    public static Optional<RuleDto> match(List<RuleDto> rules, String endpoint, String method) {
        RuleDto best = null;
        int bestRank = Integer.MAX_VALUE;
        for (RuleDto rule : rules) {
            int rank = rank(rule, endpoint, method);
            // strictly better only, so on a tie the first rule in the list stays
            if (rank < bestRank) {
                best = rule;
                bestRank = rank;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 0 is the most specific match, 3 the least; no match is MAX_VALUE. */
    private static int rank(RuleDto rule, String endpoint, String method) {
        boolean anyEndpoint = WILDCARD.equals(rule.endpoint());
        boolean anyMethod = WILDCARD.equals(rule.method());
        boolean endpointMatches = anyEndpoint || rule.endpoint().equals(endpoint);
        boolean methodMatches = anyMethod || rule.method().equalsIgnoreCase(method);
        if (!endpointMatches || !methodMatches) {
            return Integer.MAX_VALUE;
        }
        if (!anyEndpoint && !anyMethod) {
            return 0;
        }
        if (!anyEndpoint) {
            return 1;
        }
        return anyMethod ? 3 : 2;
    }
}
