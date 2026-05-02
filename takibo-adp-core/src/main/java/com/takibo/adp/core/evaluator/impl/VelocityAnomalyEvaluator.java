package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.*;
import com.takibo.adp.core.model.AccessContext;
import com.takibo.adp.core.port.BehaviorProfileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class VelocityAnomalyEvaluator implements ContextEvaluator {
    
    private static final long MIN_SAMPLE_COUNT = 30;
    private static final double MIN_STD_DEV = 0.5;
    
    private final BehaviorProfileReader profileReader;
    private final boolean enabled;
    
    @Override
    public String getName() {
        return "VelocityAnomalyEvaluator";
    }
    
    @Override
    public double getWeight() {
        return 0.20;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public EvaluatorResult evaluate(AccessContext context) {
        Integer count10s = context.getRequestCountLast10s();
        Integer count60s = context.getRequestCountLast60s();
        
        if (count10s == null && count60s == null) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(30.0)
                .confidence(0.3)
                .reason("Velocity data not available")
                .recommendation(Recommendation.ALLOW)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        Optional<BehaviorProfileView> profileOpt = profileReader.findBySubjectId(context.getSubjectId());
        
        if (profileOpt.isEmpty()) {
            return bootstrapResult(count10s, count60s);
        }
        
        BehaviorProfileView profile = profileOpt.get();
        BehaviorProfileView.VelocityStats velocity = profile.velocity();
        
        if (velocity == null || velocity.sampleCount() < MIN_SAMPLE_COUNT || velocity.stdDev() < MIN_STD_DEV) {
            return bootstrapResult(count10s, count60s);
        }
        
        double avgPerMinute = velocity.avgPerMinute();
        double stdDev = velocity.stdDev();
        
        double rpm10s = count10s != null ? count10s * 6.0 : 0.0;
        double rpm60s = count60s != null ? count60s : 0.0;
        
        double zScore10s = (rpm10s - avgPerMinute) / stdDev;
        double zScore60s = (rpm60s - avgPerMinute) / stdDev;
        double maxZ = Math.max(zScore10s, zScore60s);
        
        double riskScore = calculateRiskFromZScore(maxZ);
        
        String reason = String.format("Velocity: current=%.1f rpm, baseline=%.1f±%.1f, z-score=%.2f",
            Math.max(rpm10s, rpm60s),
            avgPerMinute,
            stdDev,
            maxZ);
        
        Recommendation recommendation = riskScore > 60.0 
            ? Recommendation.CHALLENGE 
            : Recommendation.ALLOW;
        
        return EvaluatorResult.builder()
            .evaluatorName(getName())
            .riskScore(riskScore)
            .confidence(0.8)
            .reason(reason)
            .recommendation(recommendation)
            .status(EvaluatorStatus.OK)
            .build();
    }
    
    private EvaluatorResult bootstrapResult(Integer count10s, Integer count60s) {
        int burst = count10s != null ? count10s : 0;
        int sustained = count60s != null ? count60s : 0;
        
        if (burst > 20 || sustained > 100) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(70.0)
                .confidence(0.35)
                .reason("High velocity detected (no baseline)")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        return EvaluatorResult.builder()
            .evaluatorName(getName())
            .riskScore(40.0)
            .confidence(0.35)
            .reason("Velocity baseline not established")
            .recommendation(Recommendation.CHALLENGE)
            .status(EvaluatorStatus.NO_DATA)
            .build();
    }
    
    private double calculateRiskFromZScore(double zScore) {
        if (zScore > 3.0) return 90.0;
        if (zScore > 2.0) return 70.0;
        if (zScore > 1.0) return 40.0;
        return 10.0;
    }
}
