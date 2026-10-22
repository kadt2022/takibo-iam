package com.takibo.adp.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionResponseTest {

    private static DecisionResponse response(Decision decision) {
        Thresholds thresholds = new Thresholds(75.0, 50.0, "default");
        DecisionFactor factor = new DecisionFactor("NetworkRiskEvaluator", 80.0, 0.9, "VPN", EvaluatorStatus.OK);
        return new DecisionResponse("d-1", decision, 42.0, 0.8, 0.2, "explanation", thresholds,
                List.of(factor), DecisionStatus.OK, Instant.parse("2026-10-21T14:00:00Z"), 3L);
    }

    @Test
    void exactlyOnePredicateMatchesEachDecision() {
        DecisionResponse allow = response(Decision.ALLOW);
        assertThat(allow.isAllowed()).isTrue();
        assertThat(allow.isDenied()).isFalse();
        assertThat(allow.requiresChallenge()).isFalse();

        DecisionResponse deny = response(Decision.DENY);
        assertThat(deny.isAllowed()).isFalse();
        assertThat(deny.isDenied()).isTrue();
        assertThat(deny.requiresChallenge()).isFalse();

        DecisionResponse challenge = response(Decision.CHALLENGE);
        assertThat(challenge.isAllowed()).isFalse();
        assertThat(challenge.isDenied()).isFalse();
        assertThat(challenge.requiresChallenge()).isTrue();
    }

    @Test
    void missingDecision_isNeitherAllowedNorDeniedNorChallenged() {
        DecisionResponse response = response(null);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.isDenied()).isFalse();
        assertThat(response.requiresChallenge()).isFalse();
    }

    @Test
    void carriesThresholdsAndFactors() {
        DecisionResponse response = response(Decision.ALLOW);

        assertThat(response.thresholds()).isEqualTo(new Thresholds(75.0, 50.0, "default"));
        assertThat(response.topFactors()).singleElement().satisfies(factor -> {
            assertThat(factor.evaluatorName()).isEqualTo("NetworkRiskEvaluator");
            assertThat(factor.riskScore()).isEqualTo(80.0);
            assertThat(factor.confidence()).isEqualTo(0.9);
            assertThat(factor.reason()).isEqualTo("VPN");
            assertThat(factor.status()).isEqualTo(EvaluatorStatus.OK);
        });
        assertThat(response.status()).isEqualTo(DecisionStatus.OK);
        assertThat(response.executionTimeMs()).isEqualTo(3L);
    }

    @Test
    void publicEnumsExposeTheContractValues() {
        assertThat(Decision.values()).containsExactly(Decision.ALLOW, Decision.DENY, Decision.CHALLENGE);
        assertThat(DecisionStatus.values()).containsExactly(
                DecisionStatus.OK, DecisionStatus.TIMEOUT, DecisionStatus.PARTIAL, DecisionStatus.ERROR);
        assertThat(EvaluatorStatus.values()).containsExactly(
                EvaluatorStatus.OK, EvaluatorStatus.TIMEOUT, EvaluatorStatus.ERROR, EvaluatorStatus.NO_DATA);
    }
}
