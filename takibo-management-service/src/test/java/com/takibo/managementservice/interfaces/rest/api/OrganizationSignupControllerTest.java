package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.managementservice.application.service.OrganizationSignupService;
import com.takibo.managementservice.application.result.OrganizationSignupResult;
import com.takibo.managementservice.interfaces.rest.mapper.OrganizationSignupRestMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationSignupControllerTest {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPACE_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private OrganizationSignupService service;

    private ValidatorFactory validatorFactory;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrganizationSignupController(service, new OrganizationSignupRestMapper()))
                .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                .build();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void signup_rejects_invalid_nested_fields_before_calling_the_service() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": {"code": "x", "name": ""},
                                  "space": {"code": "", "name": ""},
                                  "account": {"email": "invalid", "password": "short"},
                                  "profile": {"username": "", "firstName": "", "lastName": ""}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    assertThat(result.getResolvedException())
                            .isInstanceOf(MethodArgumentNotValidException.class);
                    MethodArgumentNotValidException exception =
                            (MethodArgumentNotValidException) result.getResolvedException();
                    assertThat(exception.getBindingResult().getFieldErrors())
                            .extracting("field")
                            .contains(
                                    "organization.code",
                                    "organization.name",
                                    "space.code",
                                    "space.name",
                                    "account.email",
                                    "account.password",
                                    "profile.username",
                                    "profile.firstName",
                                    "profile.lastName"
                            );
                });

        verifyNoInteractions(service);
    }

    @Test
    void signup_returns_created_with_the_organization_location() throws Exception {
        when(service.signup(any())).thenReturn(new OrganizationSignupResult(
                ORGANIZATION_ID,
                SPACE_ID,
                ACCOUNT_ID,
                USER_ID
        ));

        mockMvc.perform(post("/api/v1/orgs/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": {"code": "takibo-main", "name": "Takibo"},
                                  "space": {"code": "finance", "name": "Finance"},
                                  "account": {
                                    "email": "founder@takibo.io",
                                    "password": "Str0ng!Passw0rd"
                                  },
                                  "profile": {
                                    "username": "founder",
                                    "firstName": "Tresor",
                                    "lastName": "Kadima"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/v1/orgs/" + ORGANIZATION_ID
                ))
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID.toString()))
                .andExpect(jsonPath("$.spaceId").value(SPACE_ID.toString()));
    }
}
