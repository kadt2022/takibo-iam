//package com.takibo.adp.core.engine;
//
//import com.takibo.adp.api.*;
//import com.takibo.adp.core.evaluator.ContextEvaluator;
//import com.takibo.adp.core.evaluator.impl.DeviceBaselineEvaluator;
//import com.takibo.adp.core.evaluator.impl.NetworkRiskEvaluator;
//import com.takibo.adp.core.evaluator.impl.TimeRiskEvaluator;
//import com.takibo.adp.core.port.BehaviorProfileReader;
//import com.takibo.adp.core.port.ThresholdPolicy;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//
//import java.time.Instant;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Set;
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.when;
//
//class DecisionEngineTest {
//
//    @Mock
//    private BehaviorProfileReader profileReader;
//
//    @Mock
//    private ThresholdPolicy thresholdPolicy;
//
//    private DecisionEngine engine;
//    private ExecutorService executorService;
//    private AutoCloseable mocks;
//
//    @BeforeEach
//    void setUp() {
//        mocks = MockitoAnnotations.openMocks(this);
//
//        executorService = Executors.newFixedThreadPool(4);
//
//        List<ContextEvaluator> evaluators = List.of(
//                new NetworkRiskEvaluator(true),
//                new TimeRiskEvaluator(true),
//                new DeviceBaselineEvaluator(profileReader, true)
//        );
//
//        AdpExecutor executor = new AdpExecutor(executorService);
//        AggregationStrategy aggregationStrategy = new AggregationStrategy();
//
//        engine = new DecisionEngine(evaluators, thresholdPolicy, executor, aggregationStrategy);
//    }
//
//    @AfterEach
//    void tearDown() throws Exception {
//        if (executorService != null) {
//            executorService.shutdownNow();
//        }
//        if (mocks != null) {
//            mocks.close();
//        }
//    }
//
//    @Test
//    void shouldAllowNormalRequest() {
//        when(thresholdPolicy.calculate(any()))
//                .thenReturn(new Thresholds(75.0, 40.0, "baseline"));
//
//        when(profileReader.findBySubjectId(anyString()))
//                .thenReturn(Optional.of(createKnownProfile()));
//
//        DecisionRequest request = createRequest("user123", "device-abc");
//
//        DecisionResponse response = engine.evaluate(request);
//
//        assertThat(response.decision()).isEqualTo(Decision.ALLOW);
//        assertThat(response.riskScore()).isLessThan(50.0);
//        assertThat(response.confidence()).isGreaterThan(0.6);
//        assertThat(response.status()).isEqualTo(DecisionStatus.OK);
//    }
//
////    @Test
////    void shouldChallengeNewDevice() {
////        when(thresholdPolicy.calculate(any()))
////                .thenReturn(new Thresholds(75.0, 40.0, "baseline"));
////
////        when(profileReader.findBySubjectId(anyString()))
////                .thenReturn(Optional.of(createEmptyProfile()));
////
////        DecisionRequest request = createRequest("user123", "device-new");
////
////        DecisionResponse response = engine.evaluate(request);
////
////        assertThat(response.decision()).isIn(Decision.CHALLENGE, Decision.DENY);
////        assertThat(response.riskScore()).isGreaterThan(50.0);
////    }
//
//    @Test
//    void shouldDenyHighRiskNetwork() {
//        when(thresholdPolicy.calculate(any()))
//                .thenReturn(new Thresholds(75.0, 40.0, "baseline"));
//
//        when(profileReader.findBySubjectId(anyString()))
//                .thenReturn(Optional.empty());
//
//        DecisionRequest request = new DecisionRequest(
//                "user123",
//                "org1",
//                "space1",
//                Set.of("USER"),
//                Set.of(),
//                "/api/admin",
//                "POST",
//                Instant.now(),
//                "1.2.3.4",
//                "device-xyz",
//                "Mozilla",
//                null,
//                null,
//                true,   // isVpn
//                false,  // isProxy
//                false,  // isTor
//                "session1",
//                2,
//                10,
//                15,
//                "1.0",
//                Map.of()
//        );
//
//        DecisionResponse response = engine.evaluate(request);
//
//        assertThat(response.decision()).isIn(Decision.CHALLENGE, Decision.DENY);
//        assertThat(response.topFactors())
//                .anyMatch(f -> f.evaluatorName().equals("NetworkRiskEvaluator"));
//    }
//
////    @Test
////    void shouldHandleLowConfidence() {
////        when(thresholdPolicy.calculate(any()))
////                .thenReturn(new Thresholds(75.0, 40.0, "baseline"));
////
////        when(profileReader.findBySubjectId(anyString()))
////                .thenReturn(Optional.empty());
////
////        DecisionRequest request = createRequest("user-new", "device-unknown");
////
////        DecisionResponse response = engine.evaluate(request);
////
////        assertThat(response.confidence()).isLessThan(0.6);
////        assertThat(response.decision()).isEqualTo(Decision.CHALLENGE);
////        assertThat(response.explanation()).contains("confidence");
////    }
//
//    @Test
//    void shouldAdaptThresholdsForAdminResource() {
//        when(thresholdPolicy.calculate(any()))
//                .thenReturn(new Thresholds(55.0, 30.0, "admin resource"));
//
//        when(profileReader.findBySubjectId(anyString()))
//                .thenReturn(Optional.of(createKnownProfile()));
//
//        DecisionRequest request = withResourcePath(
//                createRequest("user123", "device-abc"),
//                "/api/admin/users"
//        );
//
//        DecisionResponse response = engine.evaluate(request);
//
//        assertThat(response.thresholds().denyThreshold()).isEqualTo(55.0);
//        assertThat(response.thresholds().reason()).contains("admin");
//    }
//
//    @Test
//    void shouldCompleteWithinTimeout() {
//        when(thresholdPolicy.calculate(any()))
//                .thenReturn(new Thresholds(75.0, 40.0, "baseline"));
//
//        when(profileReader.findBySubjectId(anyString()))
//                .thenReturn(Optional.of(createKnownProfile()));
//
//        DecisionRequest request = withTimeoutMs(
//                createRequest("user123", "device-abc"),
//                10
//        );
//
//        long start = System.nanoTime();
//        DecisionResponse response = engine.evaluate(request);
//        long duration = (System.nanoTime() - start) / 1_000_000;
//
//        assertThat(duration).isLessThan(15);
//        assertThat(response.executionTimeMs()).isLessThan(15);
//    }
//
//    private DecisionRequest withResourcePath(DecisionRequest r, String path) {
//        return new DecisionRequest(
//                r.subjectId(), r.organizationId(), r.spaceId(), r.roles(), r.permissions(),
//                path, r.httpMethod(),
//                r.timestamp(), r.ipAddress(), r.deviceFingerprint(), r.userAgent(),
//                r.country(), r.city(), r.isVpn(), r.isProxy(), r.isTor(),
//                r.sessionId(), r.requestCountLast10s(), r.requestCountLast60s(),
//                r.timeoutMs(), r.policyVersion(), r.metadata()
//        );
//    }
//
//    private DecisionRequest withTimeoutMs(DecisionRequest r, int timeoutMs) {
//        return new DecisionRequest(
//                r.subjectId(), r.organizationId(), r.spaceId(), r.roles(), r.permissions(),
//                r.resourcePath(), r.httpMethod(),
//                r.timestamp(), r.ipAddress(), r.deviceFingerprint(), r.userAgent(),
//                r.country(), r.city(), r.isVpn(), r.isProxy(), r.isTor(),
//                r.sessionId(), r.requestCountLast10s(), r.requestCountLast60s(),
//                timeoutMs, r.policyVersion(), r.metadata()
//        );
//    }
//
//    private DecisionRequest createRequest(String subjectId, String deviceFingerprint) {
//        return new DecisionRequest(
//                subjectId,
//                "org1",
//                "space1",
//                Set.of("USER"),
//                Set.of(),
//                "/api/dashboard",
//                "GET",
//                Instant.now(),
//                "192.168.1.1",
//                deviceFingerprint,
//                "Mozilla/5.0",
//                null,
//                null,
//                false,
//                false,
//                false,
//                "session1",
//                2,
//                10,
//                15,
//                "1.0",
//                Map.of()
//        );
//    }
//
//    private BehaviorProfileView createKnownProfile() {
//        return new BehaviorProfileView(
//                "user123",
//                Map.of(
//                        "device-abc", new BehaviorProfileView.FingerprintStats(
//                                50,
//                                Instant.now().minusSeconds(86400L * 30),
//                                Instant.now().minusSeconds(3600)
//                        )
//                ),
//                new BehaviorProfileView.VelocityStats(5.0, 2.0, 100),
//                Map.of()
//        );
//    }
//
//    private BehaviorProfileView createEmptyProfile() {
//        return new BehaviorProfileView(
//                "user123",
//                Map.of(),
//                new BehaviorProfileView.VelocityStats(0.0, 0.0, 0),
//                Map.of()
//        );
//    }
//}
