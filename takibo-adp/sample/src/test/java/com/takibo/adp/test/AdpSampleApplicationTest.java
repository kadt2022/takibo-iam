package com.takibo.adp.test;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.api.DecisionStatus;
import com.takibo.adp.api.Thresholds;
import com.takibo.adp.core.port.BehaviorProfileReader;
import com.takibo.adp.test.adapter.TestBehaviorProfileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Démarre l'application exemple complète ; seule la décision ADP est simulée pour piloter
 * le résultat de l'autorisation de façon déterministe.
 */
@SpringBootTest
class AdpSampleApplicationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AdaptiveDecisionPort adaptiveDecisionPort;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void adpDecides(Decision decision) {
        when(adaptiveDecisionPort.evaluate(any())).thenReturn(new DecisionResponse("d-1", decision, 10.0, 0.9, 0.1,
                "test", new Thresholds(75.0, 50.0, "baseline"), List.of(), DecisionStatus.OK, Instant.now(), 1L));
    }

    @Test
    void sampleProfilesReplaceTheNoopReader() {
        assertThat(context.getBean(BehaviorProfileReader.class)).isInstanceOf(TestBehaviorProfileReader.class);
    }

    @Test
    void healthAndPublicEndpoints_areOpenWithoutAdp() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/public/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This is a public endpoint"));
        verifyNoInteractions(adaptiveDecisionPort);
    }

    @Test
    void allowedUser_reachesProtectedEndpoints() throws Exception {
        adpDecides(Decision.ALLOW);

        mvc.perform(get("/api/dashboard").with(user("user").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user").value("user"));
        mvc.perform(get("/api/profile").with(user("user").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("user"));
        mvc.perform(get("/api/data/sensitive").with(user("user").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("confidential-info"));
        mvc.perform(get("/api/admin/users").with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Admin users list"));
        mvc.perform(post("/api/admin/users").with(user("admin").roles("USER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"new\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("User created"));
    }

    @Test
    void deniedOrChallengedUser_isForbidden() throws Exception {
        adpDecides(Decision.DENY);
        mvc.perform(get("/api/dashboard").with(user("user").roles("USER"))).andExpect(status().isForbidden());

        adpDecides(Decision.CHALLENGE);
        mvc.perform(get("/api/dashboard").with(user("user").roles("USER"))).andExpect(status().isForbidden());
    }
}
