package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.EvaluatorResult;
import com.takibo.adp.core.evaluator.Recommendation;
import com.takibo.adp.core.model.AccessContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkRiskEvaluatorTest {

    private final NetworkRiskEvaluator evaluator = new NetworkRiskEvaluator(true);

    private EvaluatorResult evaluate(Boolean vpn, Boolean proxy, Boolean tor) {
        return evaluator.evaluate(AccessContext.builder()
                .subjectId("alice").isVpn(vpn).isProxy(proxy).isTor(tor).build());
    }

    @Test
    void describesItself() {
        assertThat(evaluator.getName()).isEqualTo("NetworkRiskEvaluator");
        assertThat(evaluator.getWeight()).isEqualTo(0.15);
        assertThat(evaluator.isEnabled()).isTrue();
        assertThat(new NetworkRiskEvaluator(false).isEnabled()).isFalse();
    }

    @Test
    void missingOrFalseNetworkFlags_areNormal() {
        for (EvaluatorResult result : new EvaluatorResult[] {
                evaluate(null, null, null), evaluate(false, false, false)}) {
            assertThat(result.getEvaluatorName()).isEqualTo("NetworkRiskEvaluator");
            assertThat(result.getRiskScore()).isEqualTo(10.0);
            assertThat(result.getConfidence()).isEqualTo(0.9);
            assertThat(result.getReason()).isEqualTo("Network appears normal");
            assertThat(result.getRecommendation()).isEqualTo(Recommendation.ALLOW);
            assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
        }
    }

    @Test
    void torIsHighRisk() {
        EvaluatorResult result = evaluate(null, null, true);

        assertThat(result.getRiskScore()).isEqualTo(80.0);
        assertThat(result.getConfidence()).isEqualTo(0.9);
        assertThat(result.getReason()).isEqualTo("High risk network detected: Tor");
        assertThat(result.getRecommendation()).isEqualTo(Recommendation.CHALLENGE);
        assertThat(result.getStatus()).isEqualTo(EvaluatorStatus.OK);
    }

    @Test
    void vpnAndProxyAreHighRisk() {
        assertThat(evaluate(true, null, null).getReason()).isEqualTo("High risk network detected: VPN");
        assertThat(evaluate(false, true, false).getReason()).isEqualTo("High risk network detected: Proxy");
        assertThat(evaluate(false, true, false).getRiskScore()).isEqualTo(80.0);
    }

    @Test
    void reportedTypeFollowsTorThenVpnThenProxyPriority() {
        assertThat(evaluate(true, true, true).getReason()).isEqualTo("High risk network detected: Tor");
        assertThat(evaluate(true, true, false).getReason()).isEqualTo("High risk network detected: VPN");
    }
}
