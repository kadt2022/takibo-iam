package com.takibo.adp.test.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSecurityConfigTest {

    @Test
    void createsEncodedInMemoryUsers() {
        TestSecurityConfig config = new TestSecurityConfig(null, null, null);

        UserDetailsService service = config.userDetailsService();

        InMemoryUserDetailsManager users = (InMemoryUserDetailsManager) service;
        assertTrue(users.userExists("user"));
        assertTrue(users.userExists("admin"));
        assertTrue(users.userExists("suspicious"));
    }
}
