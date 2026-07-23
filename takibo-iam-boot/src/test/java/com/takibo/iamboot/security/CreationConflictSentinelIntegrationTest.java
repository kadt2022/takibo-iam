package com.takibo.iamboot.security;

import com.takibo.managementservice.domain.exception.ClientAlreadyExistsException;
import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.exception.OAuthClientSecretRotationConflictException;
import com.takibo.managementservice.domain.exception.SpaceCodeAlreadyExistsException;
import com.takibo.securitymanagement.sentinel.advice.SentinelAdvice;
import com.takibo.securitymanagement.sentinel.advice.SentinelErrorCode;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleHandlers;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistrar;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreationConflictSentinelIntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SentinelRuleRegistry registry = new SentinelRuleRegistry(SentinelRuleHandlers.genericRule());
        SentinelRuleRegistrar.registerDefaults(registry);
        mockMvc = MockMvcBuilders.standaloneSetup(new ConcurrentCreationController())
                .setControllerAdvice(new SentinelAdvice(registry))
                .build();
    }

    @Test
    void concurrentOrganizationCollision_returns409ThroughSentinel() throws Exception {
        mockMvc.perform(get("/test/concurrent/organization"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(SentinelErrorCode.ORGANIZATION_ALREADY_EXISTS.name()));
    }

    @Test
    void concurrentOAuthClientCollision_returns409ThroughSentinel() throws Exception {
        mockMvc.perform(get("/test/concurrent/client"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(SentinelErrorCode.OAUTH_CLIENT_ALREADY_EXISTS.name()));
    }

    @Test
    void exhaustedSpaceCodeCandidates_returns409ThroughSentinel() throws Exception {
        mockMvc.perform(get("/test/concurrent/space"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(SentinelErrorCode.SPACE_ALREADY_EXISTS.name()));
    }

    @Test
    void concurrentOAuthClientSecretRotation_returns409ThroughSentinel() throws Exception {
        mockMvc.perform(get("/test/concurrent/client-secret"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        SentinelErrorCode.OAUTH_CLIENT_SECRET_ROTATION_CONFLICT.name()));
    }

    @RestController
    static class ConcurrentCreationController {

        @GetMapping("/test/concurrent/organization")
        void organization() {
            throw new OrganizationCodeAlreadyExistsException("takibo", databaseConflict());
        }

        @GetMapping("/test/concurrent/client")
        void client() {
            throw new ClientAlreadyExistsException("machine-client", databaseConflict());
        }

        @GetMapping("/test/concurrent/client-secret")
        void clientSecret() {
            throw new OAuthClientSecretRotationConflictException();
        }

        @GetMapping("/test/concurrent/space")
        void space() {
            throw new SpaceCodeAlreadyExistsException("finance");
        }

        private static DataIntegrityViolationException databaseConflict() {
            return new DataIntegrityViolationException("concurrent unique constraint violation");
        }
    }
}
