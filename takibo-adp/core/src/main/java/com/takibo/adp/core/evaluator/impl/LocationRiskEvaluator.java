package com.takibo.adp.core.evaluator.impl;

import com.takibo.adp.api.BehaviorProfileView;
import com.takibo.adp.api.EvaluatorStatus;
import com.takibo.adp.core.evaluator.*;
import com.takibo.adp.core.model.AccessContext;
import com.takibo.adp.core.port.BehaviorProfileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class LocationRiskEvaluator implements ContextEvaluator {
    
    private final BehaviorProfileReader profileReader;
    private final boolean enabled;
    
    @Override
    public String getName() {
        return "LocationRiskEvaluator";
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
        String country = context.getCountry();
        String city = context.getCity();
        
        if (country == null && city == null) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(40.0)
                .confidence(0.3)
                .reason("No location data available")
                .recommendation(Recommendation.ALLOW)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        Optional<BehaviorProfileView> profileOpt = profileReader.findBySubjectId(context.getSubjectId());
        
        if (profileOpt.isEmpty()) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(50.0)
                .confidence(0.4)
                .reason("No behavioral profile for location analysis")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        BehaviorProfileView profile = profileOpt.get();
        Map<String, BehaviorProfileView.LocationStats> locations = profile.locations();
        
        if (locations == null || locations.isEmpty()) {
            return EvaluatorResult.builder()
                .evaluatorName(getName())
                .riskScore(55.0)
                .confidence(0.4)
                .reason("No location history available")
                .recommendation(Recommendation.CHALLENGE)
                .status(EvaluatorStatus.NO_DATA)
                .build();
        }
        
        String locationKey = buildLocationKey(country, city);
        BehaviorProfileView.LocationStats stats = locations.get(locationKey);
        
        if (stats == null) {
            if (country != null && !isKnownCountry(locations, country)) {
                return EvaluatorResult.builder()
                    .evaluatorName(getName())
                    .riskScore(85.0)
                    .confidence(0.8)
                    .reason("Login from new country: " + country)
                    .recommendation(Recommendation.CHALLENGE)
                    .status(EvaluatorStatus.OK)
                    .build();
            }
            
            if (city != null) {
                return EvaluatorResult.builder()
                    .evaluatorName(getName())
                    .riskScore(65.0)
                    .confidence(0.7)
                    .reason("Login from new city: " + city)
                    .recommendation(Recommendation.CHALLENGE)
                    .status(EvaluatorStatus.OK)
                    .build();
            }
        }
        
        return EvaluatorResult.builder()
            .evaluatorName(getName())
            .riskScore(10.0)
            .confidence(0.9)
            .reason("Location consistent with user profile")
            .recommendation(Recommendation.ALLOW)
            .status(EvaluatorStatus.OK)
            .build();
    }
    
    private String buildLocationKey(String country, String city) {
        if (country != null && city != null) {
            return country + ":" + city;
        }
        if (country != null) {
            return country;
        }
        return city;
    }
    
    private boolean isKnownCountry(Map<String, BehaviorProfileView.LocationStats> locations, String country) {
        return locations.keySet().stream()
            .anyMatch(key -> key.startsWith(country + ":") || key.equals(country));
    }
}
