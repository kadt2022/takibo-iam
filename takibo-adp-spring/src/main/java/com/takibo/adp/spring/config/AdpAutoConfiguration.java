package com.takibo.adp.spring.config;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.core.engine.*;
import com.takibo.adp.core.evaluator.ContextEvaluator;
import com.takibo.adp.core.evaluator.impl.*;
import com.takibo.adp.core.port.*;
import com.takibo.adp.spring.adapter.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableConfigurationProperties(AdpProperties.class)
public class AdpAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ExecutorService adpExecutorService(AdpProperties properties) {
        log.info("Creating ADP executor service with parallelism={}", properties.getParallelism());
        return Executors.newFixedThreadPool(
            properties.getParallelism(),
            r -> {
                Thread t = new Thread(r);
                t.setName("adp-evaluator-" + t.threadId());
                t.setDaemon(true);
                return t;
            }
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public BehaviorProfileReader behaviorProfileReader() {
        log.info("Using NoopBehaviorProfileReader (no profile storage)");
        return new NoopBehaviorProfileReader();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public BehaviorProfileWriter behaviorProfileWriter(AdpProperties properties) {
        log.info("Using BehaviorProfileWriter mode: {}", properties.getProfile().getWriter());
        
        return switch (properties.getProfile().getWriter()) {
            case NOOP -> new NoopBehaviorProfileWriter();
            case ASYNC_JPA -> {
                log.warn("ASYNC_JPA writer not yet implemented, using NOOP");
                yield new NoopBehaviorProfileWriter();
            }
        };
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ThresholdPolicy thresholdPolicy() {
        log.info("Using DefaultThresholdPolicy with adaptive thresholds");
        return new DefaultThresholdPolicy();
    }
    
    @Bean
    public List<ContextEvaluator> contextEvaluators(
        AdpProperties properties,
        BehaviorProfileReader profileReader
    ) {
        List<ContextEvaluator> evaluators = new ArrayList<>();
        
        if (properties.getFeatures().isBaseline()) {
            log.info("Enabling DeviceBaselineEvaluator");
            evaluators.add(new DeviceBaselineEvaluator(profileReader, true));
        }
        
        if (properties.getFeatures().isVelocity()) {
            log.info("Enabling VelocityAnomalyEvaluator");
            evaluators.add(new VelocityAnomalyEvaluator(profileReader, true));
        }
        
        if (properties.getFeatures().isNetwork()) {
            log.info("Enabling NetworkRiskEvaluator");
            evaluators.add(new NetworkRiskEvaluator(true));
        }
        
        if (properties.getFeatures().isTime()) {
            log.info("Enabling TimeRiskEvaluator");
            evaluators.add(new TimeRiskEvaluator(true));
        }
        
        if (properties.getFeatures().isLocation()) {
            log.info("Enabling LocationRiskEvaluator");
            evaluators.add(new LocationRiskEvaluator(profileReader, true));
        }
        
        log.info("Configured {} evaluators", evaluators.size());
        return evaluators;
    }
    
    @Bean
    public AggregationStrategy aggregationStrategy() {
        return new AggregationStrategy();
    }
    
    @Bean
    public AdpExecutor adpExecutor(ExecutorService adpExecutorService) {
        return new AdpExecutor(adpExecutorService);
    }
    
    @Bean
    public DecisionEngine decisionEngine(
        List<ContextEvaluator> evaluators,
        ThresholdPolicy thresholdPolicy,
        AdpExecutor executor,
        AggregationStrategy aggregationStrategy
    ) {
        log.info("Creating DecisionEngine with {} evaluators", evaluators.size());
        return new DecisionEngine(evaluators, thresholdPolicy, executor, aggregationStrategy);
    }
    
    @Bean
    public RequestVelocityTracker requestVelocityTracker() {
        return new RequestVelocityTracker();
    }
    
    @Bean
    public AdpContextEnricher adpContextEnricher(
        AdpProperties properties,
        RequestVelocityTracker velocityTracker
    ) {
        return new AdpContextEnricher(properties, velocityTracker);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AdaptiveDecisionPort adaptiveDecisionPort(
        DecisionEngine engine,
        BehaviorProfileWriter profileWriter,
        AdpProperties properties
    ) {
        log.info("Creating AdaptiveDecisionPort (ADP enabled={})", properties.isEnabled());
        return new SpringAdaptiveDecisionService(engine, profileWriter, properties);
    }
}
