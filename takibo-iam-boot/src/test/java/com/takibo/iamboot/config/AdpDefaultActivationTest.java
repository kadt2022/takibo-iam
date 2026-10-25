package com.takibo.iamboot.config;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.spring.config.AdpProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activation de l'ADP dans l'application assemblée : désactivé par défaut (POC), le composant
 * reste câblé et se réactive explicitement. La requête sondée cumule les signaux qui faisaient
 * refuser des accès légitimes (proxy, dimanche, 3 h) : désactivé, l'ADP rend une décision
 * neutre sans évaluer ; réactivé, le moteur l'évalue réellement.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdpDefaultActivationTest {

    @Autowired
    private AdpProperties properties;

    @Autowired
    private AdaptiveDecisionPort adaptiveDecisionPort;

    private static DecisionRequest proxiedNightRequest() {
        return new DecisionRequest("alice", "org", "space", Set.of(), Set.of(), "/api/v1/me", "GET",
                Instant.parse("2026-10-25T03:00:00Z"), "203.0.113.7", null, null, null, null,
                null, true, null, null, null, null, 1000, "1.0", Map.of());
    }

    @Test
    void adpIsDisabledByDefault_andReturnsANeutralDecisionWithoutEvaluating() {
        assertThat(properties.isEnabled()).isFalse();

        DecisionResponse response = adaptiveDecisionPort.evaluate(proxiedNightRequest());

        assertThat(response.decisionId()).isEqualTo("disabled");
        assertThat(response.decision()).isEqualTo(Decision.ALLOW);
    }

    @Nested
    @TestPropertySource(properties = "takibo.adp.enabled=true")
    class ExplicitlyEnabled {

        @Autowired
        private AdpProperties enabledProperties;

        @Autowired
        private AdaptiveDecisionPort enabledPort;

        @Test
        void adpCanBeEnabledExplicitly_andTheEngineEvaluatesAgain() {
            assertThat(enabledProperties.isEnabled()).isTrue();

            DecisionResponse response = enabledPort.evaluate(proxiedNightRequest());

            assertThat(response.decisionId()).isNotEqualTo("disabled");
        }
    }
}
