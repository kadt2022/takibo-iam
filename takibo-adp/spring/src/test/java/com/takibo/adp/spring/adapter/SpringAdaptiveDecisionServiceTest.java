package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.api.DecisionStatus;
import com.takibo.adp.api.Thresholds;
import com.takibo.adp.core.engine.DecisionEngine;
import com.takibo.adp.core.port.BehaviorProfileWriter;
import com.takibo.adp.spring.config.AdpProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpringAdaptiveDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T14:00:00Z");
    private static final DecisionRequest REQUEST = new DecisionRequest("alice", "org", "space", Set.of(), Set.of(),
            "/api/data", "GET", NOW, null, null, null, null, null, null, null, null, null, null, null, 15, "1.0", Map.of());

    private final DecisionEngine engine = mock(DecisionEngine.class);
    private final BehaviorProfileWriter writer = mock(BehaviorProfileWriter.class);
    private final AdpProperties properties = new AdpProperties();
    private final SpringAdaptiveDecisionService service = new SpringAdaptiveDecisionService(engine, writer, properties);

    private static DecisionResponse engineResponse(Decision decision) {
        return new DecisionResponse("d-1", decision, 10.0, 0.9, 0.1, "explanation",
                new Thresholds(75.0, 50.0, "baseline"), List.of(), DecisionStatus.OK, NOW, 2L);
    }

    @Test
    void disabled_returnsNeutralAllowWithoutCallingEngineOrWriter() {
        properties.setEnabled(false);

        DecisionResponse response = service.evaluate(REQUEST);

        assertThat(response.decisionId()).isEqualTo("disabled");
        assertThat(response.decision()).isEqualTo(Decision.ALLOW);
        assertThat(response.riskScore()).isEqualTo(50.0);
        assertThat(response.confidence()).isEqualTo(0.5);
        assertThat(response.uncertainty()).isEqualTo(0.5);
        assertThat(response.explanation()).isEqualTo("ADP is disabled");
        assertThat(response.thresholds()).isEqualTo(new Thresholds(75.0, 40.0, "disabled"));
        assertThat(response.topFactors()).isEmpty();
        assertThat(response.status()).isEqualTo(DecisionStatus.OK);
        assertThat(response.timestamp()).isEqualTo(NOW);
        assertThat(response.executionTimeMs()).isZero();
        verifyNoInteractions(engine, writer);
    }

    @Test
    void allowedDecision_isReturnedAndRecordedAsSuccess() {
        DecisionResponse allow = engineResponse(Decision.ALLOW);
        when(engine.evaluate(REQUEST)).thenReturn(allow);

        assertThat(service.evaluate(REQUEST)).isSameAs(allow);
        verify(writer).recordSuccessfulAccess("alice", REQUEST);
    }

    @Test
    void deniedOrChallengedDecision_isReturnedAndRecordedAsFailure() {
        for (Decision decision : new Decision[] {Decision.DENY, Decision.CHALLENGE}) {
            BehaviorProfileWriter failureWriter = mock(BehaviorProfileWriter.class);
            DecisionResponse expected = engineResponse(decision);
            when(engine.evaluate(REQUEST)).thenReturn(expected);

            assertThat(new SpringAdaptiveDecisionService(engine, failureWriter, properties).evaluate(REQUEST))
                    .isSameAs(expected);
            verify(failureWriter).recordFailedAttempt("alice", REQUEST);
        }
    }

    @Test
    void engineFailure_failsClosedWithChallengeAndErrorStatus() {
        when(engine.evaluate(REQUEST)).thenThrow(new IllegalStateException("pool exhausted"));

        DecisionResponse response = service.evaluate(REQUEST);

        assertThat(response.decisionId()).isEqualTo("error");
        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
        assertThat(response.riskScore()).isEqualTo(50.0);
        assertThat(response.confidence()).isZero();
        assertThat(response.uncertainty()).isEqualTo(1.0);
        assertThat(response.explanation()).isEqualTo("ADP error: pool exhausted");
        assertThat(response.thresholds()).isEqualTo(new Thresholds(75.0, 40.0, "error fallback"));
        assertThat(response.topFactors()).isEmpty();
        assertThat(response.status()).isEqualTo(DecisionStatus.ERROR);
        assertThat(response.timestamp()).isEqualTo(NOW);
        assertThat(response.isAllowed()).isFalse();
        verifyNoInteractions(writer);
    }

    @Test
    void writerFailure_alsoFailsClosed() {
        when(engine.evaluate(REQUEST)).thenReturn(engineResponse(Decision.ALLOW));
        doThrow(new IllegalStateException("profile store down"))
                .when(writer).recordSuccessfulAccess("alice", REQUEST);

        DecisionResponse response = service.evaluate(REQUEST);

        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
        assertThat(response.status()).isEqualTo(DecisionStatus.ERROR);
        assertThat(response.explanation()).isEqualTo("ADP error: profile store down");
    }
}
