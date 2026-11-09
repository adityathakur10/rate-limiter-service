package com.ratelimiter.core.service;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMatcherTest {

    private final UUID tenantId = UUID.randomUUID();

    // the limit doubles as the rule's name in the assertions
    private RuleDto rule(String endpoint, String method, int limit) {
        return new RuleDto(UUID.randomUUID(), tenantId, endpoint, method, limit, 60,
                AlgorithmType.FIXED_WINDOW, null, null);
    }

    private final RuleDto exact = rule("/pay", "POST", 1);
    private final RuleDto anyMethod = rule("/pay", "*", 2);
    private final RuleDto anyEndpoint = rule("*", "POST", 3);
    private final RuleDto everything = rule("*", "*", 4);

    private int matchedLimit(List<RuleDto> rules, String endpoint, String method) {
        return RuleMatcher.match(rules, endpoint, method).orElseThrow().limit();
    }

    @Test
    void exactEndpointAndMethodWinsOverEverything() {
        assertThat(matchedLimit(List.of(everything, anyEndpoint, anyMethod, exact), "/pay", "POST")).isEqualTo(1);
    }

    @Test
    void exactEndpointWithAnyMethodBeatsAnyEndpointWithExactMethod() {
        // both match POST /pay; the endpoint is the more specific half
        assertThat(matchedLimit(List.of(everything, anyEndpoint, anyMethod), "/pay", "POST")).isEqualTo(2);
    }

    @Test
    void anyEndpointWithExactMethodBeatsTheCatchAll() {
        assertThat(matchedLimit(List.of(everything, anyEndpoint), "/orders", "POST")).isEqualTo(3);
    }

    @Test
    void theCatchAllIsTheLastResort() {
        assertThat(matchedLimit(List.of(exact, anyMethod, anyEndpoint, everything), "/orders", "GET")).isEqualTo(4);
    }

    @Test
    void theOrderOfTheListDoesNotMatter() {
        List<RuleDto> leastSpecificFirst = List.of(everything, anyEndpoint, anyMethod, exact);
        List<RuleDto> mostSpecificFirst = List.of(exact, anyMethod, anyEndpoint, everything);

        assertThat(matchedLimit(leastSpecificFirst, "/pay", "POST")).isEqualTo(1);
        assertThat(matchedLimit(mostSpecificFirst, "/pay", "POST")).isEqualTo(1);
    }

    @Test
    void aRuleForAnotherEndpointOrMethodDoesNotMatch() {
        List<RuleDto> rules = List.of(exact, anyMethod);

        assertThat(RuleMatcher.match(rules, "/orders", "POST")).isEmpty();
        assertThat(RuleMatcher.match(List.of(exact), "/pay", "GET")).isEmpty();
    }

    @Test
    void noRulesMeansNoMatch() {
        assertThat(RuleMatcher.match(List.of(), "/pay", "POST")).isEmpty();
    }

    @Test
    void theMethodIgnoresCase() {
        assertThat(matchedLimit(List.of(exact), "/pay", "post")).isEqualTo(1);
        assertThat(matchedLimit(List.of(rule("/pay", "post", 7)), "/pay", "POST")).isEqualTo(7);
    }

    @Test
    void theEndpointMustMatchExactly() {
        assertThat(RuleMatcher.match(List.of(exact), "/Pay", "POST")).isEmpty();
        assertThat(RuleMatcher.match(List.of(exact), "/pay/", "POST")).isEmpty();
        assertThat(RuleMatcher.match(List.of(exact), "/pay/send", "POST")).isEmpty();
    }

    @Test
    void onATieTheFirstRuleInTheListWins() {
        // only possible if two rules differ just by the case of the method
        RuleDto upper = rule("/pay", "POST", 8);
        RuleDto lower = rule("/pay", "post", 9);

        assertThat(matchedLimit(List.of(upper, lower), "/pay", "POST")).isEqualTo(8);
        assertThat(matchedLimit(List.of(lower, upper), "/pay", "POST")).isEqualTo(9);
    }
}
