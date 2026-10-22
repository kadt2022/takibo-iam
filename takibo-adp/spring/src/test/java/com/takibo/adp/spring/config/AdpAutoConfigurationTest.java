package com.takibo.adp.spring.config;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.api.Thresholds;
import com.takibo.adp.core.engine.AdpExecutor;
import com.takibo.adp.core.engine.AggregationStrategy;
import com.takibo.adp.core.engine.DecisionEngine;
import com.takibo.adp.core.evaluator.ContextEvaluator;
import com.takibo.adp.core.port.BehaviorProfileReader;
import com.takibo.adp.core.port.BehaviorProfileWriter;
import com.takibo.adp.core.port.ThresholdPolicy;
import com.takibo.adp.spring.adapter.AdpContextEnricher;
import com.takibo.adp.spring.adapter.DefaultThresholdPolicy;
import com.takibo.adp.spring.adapter.NoopBehaviorProfileReader;
import com.takibo.adp.spring.adapter.NoopBehaviorProfileWriter;
import com.takibo.adp.spring.adapter.RequestVelocityTracker;
import com.takibo.adp.spring.adapter.SpringAdaptiveDecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AdpAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdpAutoConfiguration.class));

    @SuppressWarnings("unchecked")
    private static List<String> evaluatorNames(org.springframework.context.ApplicationContext context) {
        return ((List<ContextEvaluator>) context.getBean("contextEvaluators")).stream()
                .map(ContextEvaluator::getName).toList();
    }

    private static DecisionRequest request() {
        return new DecisionRequest("alice", "org", "space", Set.of(), Set.of(), "/api/data", "GET",
                Instant.parse("2026-10-21T14:00:00Z"), null, null, null, null, null, null, null, null, null,
                null, null, 1000, "1.0", Map.of());
    }

    @Test
    void defaults_wireTheFullAdpStackWithAllEvaluators() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(BehaviorProfileReader.class).isInstanceOf(NoopBehaviorProfileReader.class);
            assertThat(context).getBean(BehaviorProfileWriter.class).isInstanceOf(NoopBehaviorProfileWriter.class);
            assertThat(context).getBean(ThresholdPolicy.class).isInstanceOf(DefaultThresholdPolicy.class);
            assertThat(context).getBean(AdaptiveDecisionPort.class).isInstanceOf(SpringAdaptiveDecisionService.class);
            assertThat(context).hasSingleBean(DecisionEngine.class)
                    .hasSingleBean(AdpExecutor.class)
                    .hasSingleBean(AggregationStrategy.class)
                    .hasSingleBean(RequestVelocityTracker.class)
                    .hasSingleBean(AdpContextEnricher.class)
                    .hasSingleBean(ExecutorService.class);
            assertThat(evaluatorNames(context)).containsExactly(
                    "DeviceBaselineEvaluator", "VelocityAnomalyEvaluator", "NetworkRiskEvaluator",
                    "TimeRiskEvaluator", "LocationRiskEvaluator");

            AdpProperties properties = context.getBean(AdpProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getTimeoutMs()).isEqualTo(15);
            assertThat(properties.getParallelism()).isEqualTo(8);
            assertThat(properties.getPolicyVersion()).isEqualTo("1.0");
            assertThat(properties.getProfile().getWriter()).isEqualTo(AdpProperties.WriterMode.NOOP);
            assertThat(properties.getProfile().getAsyncQueueSize()).isEqualTo(1000);
            assertThat(properties.getProfile().getAsyncBatchSize()).isEqualTo(50);
        });
    }

    @Test
    void propertiesAreBoundFromTakiboAdpPrefix() {
        runner.withPropertyValues(
                "takibo.adp.enabled=false",
                "takibo.adp.timeout-ms=40",
                "takibo.adp.parallelism=2",
                "takibo.adp.policy-version=2.0",
                "takibo.adp.features.thresholds=false",
                "takibo.adp.features.uncertainty=false",
                "takibo.adp.profile.async-queue-size=10",
                "takibo.adp.profile.async-batch-size=5"
        ).run(context -> {
            AdpProperties properties = context.getBean(AdpProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getTimeoutMs()).isEqualTo(40);
            assertThat(properties.getParallelism()).isEqualTo(2);
            assertThat(properties.getPolicyVersion()).isEqualTo("2.0");
            assertThat(properties.getFeatures().isThresholds()).isFalse();
            assertThat(properties.getFeatures().isUncertainty()).isFalse();
            assertThat(properties.getProfile().getAsyncQueueSize()).isEqualTo(10);
            assertThat(properties.getProfile().getAsyncBatchSize()).isEqualTo(5);
        });
    }

    @Test
    void featureFlags_disableIndividualEvaluators() {
        runner.withPropertyValues(
                "takibo.adp.features.baseline=false",
                "takibo.adp.features.time=false",
                "takibo.adp.features.location=false"
        ).run(context -> assertThat(evaluatorNames(context))
                .containsExactly("VelocityAnomalyEvaluator", "NetworkRiskEvaluator"));

        runner.withPropertyValues(
                "takibo.adp.features.baseline=false",
                "takibo.adp.features.velocity=false",
                "takibo.adp.features.network=false",
                "takibo.adp.features.time=false",
                "takibo.adp.features.location=false"
        ).run(context -> assertThat(evaluatorNames(context)).isEmpty());
    }

    @Test
    void asyncJpaWriterMode_fallsBackToNoopWriter() {
        runner.withPropertyValues("takibo.adp.profile.writer=ASYNC_JPA")
                .run(context -> assertThat(context).getBean(BehaviorProfileWriter.class)
                        .isInstanceOf(NoopBehaviorProfileWriter.class));
    }

    @Test
    void executorThreads_areNamedDaemonThreads() {
        runner.withPropertyValues("takibo.adp.parallelism=1").run(context -> {
            AtomicReference<Thread> worker = new AtomicReference<>();
            context.getBean(ExecutorService.class).submit(() -> worker.set(Thread.currentThread())).get();

            assertThat(worker.get().getName()).startsWith("adp-evaluator-");
            assertThat(worker.get().isDaemon()).isTrue();
        });
    }

    @Test
    void applicationBeans_replaceTheDefaults() {
        runner.withUserConfiguration(CustomBeans.class).run(context -> {
            assertThat(context).getBean(BehaviorProfileReader.class).isSameAs(CustomBeans.READER);
            assertThat(context).getBean(BehaviorProfileWriter.class).isSameAs(CustomBeans.WRITER);
            assertThat(context).getBean(ThresholdPolicy.class).isSameAs(CustomBeans.POLICY);
            assertThat(context).getBean(ExecutorService.class).isSameAs(CustomBeans.EXECUTOR);
            assertThat(context).getBean(AdaptiveDecisionPort.class).isSameAs(CustomBeans.PORT);
        });
        CustomBeans.EXECUTOR.shutdownNow();
    }

    @Test
    void wiredPort_evaluatesARequestEndToEnd() {
        runner.run(context -> {
            DecisionResponse response = context.getBean(AdaptiveDecisionPort.class).evaluate(request());

            assertThat(response.decision()).isNotNull();
            assertThat(response.status()).isNotNull();
            assertThat(response.thresholds()).isEqualTo(
                    context.getBean(ThresholdPolicy.class).calculate(request()));
        });
    }

    @Test
    void disabledAdp_returnsNeutralAllow() {
        runner.withPropertyValues("takibo.adp.enabled=false").run(context -> {
            DecisionResponse response = context.getBean(AdaptiveDecisionPort.class).evaluate(request());

            assertThat(response.decision()).isEqualTo(Decision.ALLOW);
            assertThat(response.decisionId()).isEqualTo("disabled");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeans {
        static final BehaviorProfileReader READER = subjectId -> Optional.empty();
        static final BehaviorProfileWriter WRITER = new NoopBehaviorProfileWriter();
        static final ThresholdPolicy POLICY = request -> new Thresholds(90.0, 60.0, "custom");
        static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
        static final AdaptiveDecisionPort PORT = request -> null;

        @Bean BehaviorProfileReader customReader() { return READER; }
        @Bean BehaviorProfileWriter customWriter() { return WRITER; }
        @Bean ThresholdPolicy customPolicy() { return POLICY; }
        @Bean ExecutorService customExecutor() { return EXECUTOR; }
        @Bean AdaptiveDecisionPort customPort() { return PORT; }
    }
}
