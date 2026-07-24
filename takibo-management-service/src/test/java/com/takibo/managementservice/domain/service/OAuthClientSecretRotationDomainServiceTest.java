package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.validation.OAuthClientConfigurationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthClientSecretRotationDomainServiceTest {

    @Mock
    private OAuthClientConfigurationValidator configurationValidator;

    private OAuthClientSecretRotationDomainService service;

    @BeforeEach
    void setUp() {
        service = new OAuthClientSecretRotationDomainService(
                configurationValidator
        );
    }

    @Test
    void rejects_clients_that_do_not_use_secrets() {
        OAuthClient publicClient = OAuthClient.builder()
                .clientType(ClientType.PUBLIC)
                .tokenEndpointAuthMethod(TokenEndpointAuthMethod.none)
                .build();

        assertThatThrownBy(() -> service.resolveExpiration(publicClient, null))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("client does not use secrets");
    }

    @Test
    void keeps_existing_expiration_when_none_is_requested() {
        Instant existingExpiration = Instant.parse("2035-01-01T00:00:00Z");
        OAuthClient confidentialClient = OAuthClient.builder()
                .clientType(ClientType.CONFIDENTIAL)
                .tokenEndpointAuthMethod(
                        TokenEndpointAuthMethod.client_secret_basic
                )
                .clientSecretExpiresAt(existingExpiration)
                .build();

        assertThat(service.resolveExpiration(confidentialClient, null))
                .isEqualTo(existingExpiration);
        verify(configurationValidator)
                .validateSecretExpiration(existingExpiration);
    }

    @Test
    void validates_requested_expiration_before_loading_a_client() {
        Instant requestedExpiration = Instant.parse("2035-02-01T00:00:00Z");

        service.validateRequestedExpiration(requestedExpiration);

        verify(configurationValidator)
                .validateSecretExpiration(requestedExpiration);
    }
}
