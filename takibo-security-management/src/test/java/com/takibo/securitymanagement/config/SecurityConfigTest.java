package com.takibo.securitymanagement.config;

import com.takibo.securitymanagement.infrastructure.adp.PolicyBasedAuthorizationManager;
import com.takibo.securitymanagement.infrastructure.security.JwtAuthenticationFilter;
import com.takibo.securitymanagement.infrastructure.security.boundary.OrgBoundaryFilter;
import com.takibo.securitymanagement.sentinel.http.SentinelAccessDeniedHandler;
import com.takibo.securitymanagement.sentinel.http.SentinelAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitWebConfig(SecurityConfigTest.TestApplication.class)
class SecurityConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestApplication {
    }

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private OrgBoundaryFilter orgBoundaryFilter;

    @MockitoBean
    private PolicyBasedAuthorizationManager policyBasedAuthorizationManager;

    @MockitoBean
    private SentinelAuthenticationEntryPoint sentinelAuthenticationEntryPoint;

    @MockitoBean
    private SentinelAccessDeniedHandler sentinelAccessDeniedHandler;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void definesSecurityFilterChainWithActuatorBoundary() {
        assertThat(securityFilterChain).isNotNull();
    }
}
