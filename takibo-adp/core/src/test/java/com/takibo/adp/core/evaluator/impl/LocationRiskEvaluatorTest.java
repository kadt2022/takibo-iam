package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.api.BehaviorProfileView.LocationStats;
import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.AccessContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LocationRiskEvaluatorTest {

    private static final Instant SEEN = Instant.parse("2026-10-01T00:00:00Z");

    private static LocationRiskEvaluator withLocations(Map<String, LocationStats> locations) {
        BehaviorProfileView profile = new BehaviorProfileView("alice", Map.of(), null, locations);
        return new LocationRiskEvaluator(subjectId -> "alice".equals(subjectId) ? Optional.of(profile) : Optional.empty(), true);
    }

    private static final LocationRiskEvaluator PARIS_USER = withLocations(Map.of(
            "FR:Paris", new LocationStats("FR", "Paris", 12, SEEN),
            "BE", new LocationStats("BE", null, 2, SEEN)));

    private static EvaluatorResult evaluate(LocationRiskEvaluator evaluator, String country, String city) {
        return evaluator.evaluate(AccessContext.builder().subjectId("alice").country(country).city(city).build());
    }

    private static void assertResult(EvaluatorResult result, double score, double confidence, String reason,
                                     Recommendation recommendation, EvaluatorStatus status) {
        assertThat(result.getEvaluatorName()).isEqualTo("LocationRiskEvaluator");
        assertThat(result.getRiskScore()).isEqualTo(score);
        assertThat(result.getConfidence()).isEqualTo(confidence);
        assertThat(result.getReason()).isEqualTo(reason);
        assertThat(result.getRecommendation()).isEqualTo(recommendation);
        assertThat(result.getStatus()).isEqualTo(status);
    }

    @Test
    void describesItself() {
        assertThat(PARIS_USER.getName()).isEqualTo("LocationRiskEvaluator");
        assertThat(PARIS_USER.getWeight()).isEqualTo(0.20);
        assertThat(PARIS_USER.isEnabled()).isTrue();
        assertThat(new LocationRiskEvaluator(id -> Optional.empty(), false).isEnabled()).isFalse();
    }

    @Test
    void noLocationData_isNoDataAndDoesNotReadProfile() {
        LocationRiskEvaluator evaluator = new LocationRiskEvaluator(id -> {
            throw new AssertionError("profile must not be read without location");
        }, true);

        assertResult(evaluate(evaluator, null, null), 40.0, 0.3, "No location data available",
                Recommendation.ALLOW, EvaluatorStatus.NO_DATA);
    }

    @Test
    void unknownSubject_isNoData() {
        LocationRiskEvaluator evaluator = new LocationRiskEvaluator(id -> Optional.empty(), true);

        assertResult(evaluate(evaluator, "FR", "Paris"), 50.0, 0.4, "No behavioral profile for location analysis",
                Recommendation.CHALLENGE, EvaluatorStatus.NO_DATA);
    }

    @Test
    void emptyOrMissingLocationHistory_isNoData() {
        for (LocationRiskEvaluator evaluator : new LocationRiskEvaluator[] {withLocations(Map.of()), withLocations(null)}) {
            assertResult(evaluate(evaluator, "FR", "Paris"), 55.0, 0.4, "No location history available",
                    Recommendation.CHALLENGE, EvaluatorStatus.NO_DATA);
        }
    }

    @Test
    void knownCountryAndCity_isConsistent() {
        assertResult(evaluate(PARIS_USER, "FR", "Paris"), 10.0, 0.9, "Location consistent with user profile",
                Recommendation.ALLOW, EvaluatorStatus.OK);
    }

    @Test
    void knownCountryOnlyKey_isConsistent() {
        assertResult(evaluate(PARIS_USER, "BE", null), 10.0, 0.9, "Location consistent with user profile",
                Recommendation.ALLOW, EvaluatorStatus.OK);
    }

    @Test
    void newCountry_isHighRisk() {
        assertResult(evaluate(PARIS_USER, "BR", "Rio"), 85.0, 0.8, "Login from new country: BR",
                Recommendation.CHALLENGE, EvaluatorStatus.OK);
        assertResult(evaluate(PARIS_USER, "BR", null), 85.0, 0.8, "Login from new country: BR",
                Recommendation.CHALLENGE, EvaluatorStatus.OK);
    }

    @Test
    void newCityInKnownCountry_isMediumRisk() {
        assertResult(evaluate(PARIS_USER, "FR", "Lyon"), 65.0, 0.7, "Login from new city: Lyon",
                Recommendation.CHALLENGE, EvaluatorStatus.OK);
        // Un pays connu uniquement sous la forme "BE" compte aussi.
        assertResult(evaluate(PARIS_USER, "BE", "Namur"), 65.0, 0.7, "Login from new city: Namur",
                Recommendation.CHALLENGE, EvaluatorStatus.OK);
    }

    @Test
    void knownCountryWithoutCity_whenOnlyCountryCityKeysExist_isConsistent() {
        assertResult(evaluate(PARIS_USER, "FR", null), 10.0, 0.9, "Location consistent with user profile",
                Recommendation.ALLOW, EvaluatorStatus.OK);
    }

    @Test
    void cityWithoutCountry_isLookedUpByCityAlone() {
        LocationRiskEvaluator evaluator = withLocations(Map.of("Paris", new LocationStats(null, "Paris", 3, SEEN)));

        assertResult(evaluate(evaluator, null, "Paris"), 10.0, 0.9, "Location consistent with user profile",
                Recommendation.ALLOW, EvaluatorStatus.OK);
        assertResult(evaluate(evaluator, null, "Lyon"), 65.0, 0.7, "Login from new city: Lyon",
                Recommendation.CHALLENGE, EvaluatorStatus.OK);
    }

    @Test
    void countryPrefixMustMatchExactly() {
        // "F" n'est pas un préfixe de pays de "FR:Paris" : le séparateur ":" est requis.
        assertResult(evaluate(PARIS_USER, "F", null), 85.0, 0.8, "Login from new country: F",
                Recommendation.CHALLENGE, EvaluatorStatus.OK);
    }
}
