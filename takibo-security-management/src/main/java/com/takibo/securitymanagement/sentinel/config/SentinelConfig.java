package com.takibo.securitymanagement.sentinel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.securitymanagement.sentinel.http.SentinelHttpErrorWriter;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleHandlers;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistrar;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentinelConfig {

    @Bean
    public SentinelRuleRegistry ruleRegistry() {
        SentinelRuleRegistry registry = new SentinelRuleRegistry(SentinelRuleHandlers.genericRule());
        SentinelRuleRegistrar.registerDefaults(registry);
        return registry;
    }

    @Bean
    public SentinelHttpErrorWriter sentinelHttpErrorWriter(SentinelRuleRegistry sentinelRuleRegistry,
                                                           ObjectMapper objectMapper) {
        return new SentinelHttpErrorWriter(sentinelRuleRegistry, objectMapper);
    }
}

