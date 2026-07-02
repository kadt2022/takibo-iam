package com.takibo.identitycore.interfaces.rest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.application.auth.mapper.AuthMapper;
import com.takibo.identitycore.application.auth.port.HumanLoginCase;
import com.takibo.identitycore.interfaces.rest.request.LoginRequest;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HumanLoginCase humanLoginCase;
    private AuthMapper authMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        humanLoginCase = mock(HumanLoginCase.class);
        authMapper = mock(AuthMapper.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(humanLoginCase, authMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    void login_validPayload_returnsTokenResponse() throws Exception {
        LoginRequest request = validRequest();
        LoginCommand command = new LoginCommand("founder@takibo.io", "Str0ng!Passw0rd", "takibo-iam", "finance");
        when(authMapper.toCommand(request)).thenReturn(command);
        when(humanLoginCase.login(command)).thenReturn(new LoginResponse(
                "human.jwt",
                "Bearer",
                300,
                "SPACE",
                ORG_ID,
                SPACE_ID,
                ACCOUNT_ID,
                USER_ID
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("human.jwt")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(300)))
                .andExpect(jsonPath("$.scopeLevel", is("SPACE")))
                .andExpect(jsonPath("$.organizationId", is(ORG_ID.toString())))
                .andExpect(jsonPath("$.spaceId", is(SPACE_ID.toString())))
                .andExpect(jsonPath("$.accountId", is(ACCOUNT_ID.toString())))
                .andExpect(jsonPath("$.userId", is(USER_ID.toString())));

        verify(humanLoginCase).login(command);
    }

    @Test
    void login_blankEmail_returnsBadRequest() throws Exception {
        assertBadRequest(new LoginRequest(" ", "Str0ng!Passw0rd", "takibo-iam", "finance"));
    }

    @Test
    void login_blankPassword_returnsBadRequest() throws Exception {
        assertBadRequest(new LoginRequest("founder@takibo.io", " ", "takibo-iam", "finance"));
    }

    @Test
    void login_blankOrgCode_returnsBadRequest() throws Exception {
        assertBadRequest(new LoginRequest("founder@takibo.io", "Str0ng!Passw0rd", " ", "finance"));
    }

    @Test
    void login_blankSpaceCode_returnsBadRequest() throws Exception {
        assertBadRequest(new LoginRequest("founder@takibo.io", "Str0ng!Passw0rd", "takibo-iam", " "));
    }

    private void assertBadRequest(LoginRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private LoginRequest validRequest() {
        return new LoginRequest("founder@takibo.io", "Str0ng!Passw0rd", "takibo-iam", "finance");
    }
}
