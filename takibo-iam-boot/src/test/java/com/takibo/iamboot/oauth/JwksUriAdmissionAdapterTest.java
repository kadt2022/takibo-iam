package com.takibo.iamboot.oauth;

import com.takibo.authorizationserver.infrastructure.jwks.OutboundDestinationPolicy;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwksUriAdmissionAdapterTest {

    private final JwksUriAdmissionAdapter defaults =
            new JwksUriAdmissionAdapter(new OutboundDestinationPolicy(List.of(), List.of(), false));

    @Test
    void a_public_https_destination_is_admitted() {
        assertThatCode(() -> defaults.requireAllowed(URI.create("https://keys.example.com/jwks.json")))
                .doesNotThrowAnyException();
    }

    @Test
    void a_refusal_becomes_a_client_configuration_error_naming_the_field() {
        URI metadata = URI.create("https://169.254.169.254/latest/meta-data/");
        URI plainHttp = URI.create("http://keys.example.com/jwks.json");

        assertThatThrownBy(() -> defaults.requireAllowed(metadata))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessageStartingWith("jwksUri is not an allowed destination")
                .hasMessageContaining("169.254.169.254");
        assertThatThrownBy(() -> defaults.requireAllowed(plainHttp))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void a_trusted_development_destination_is_admitted_over_http() {
        JwksUriAdmissionAdapter dev = new JwksUriAdmissionAdapter(
                new OutboundDestinationPolicy(List.of("keycloak"), List.of(), true));

        assertThatCode(() -> dev.requireAllowed(URI.create(
                "http://keycloak:8080/realms/takibo/protocol/openid-connect/certs"))).doesNotThrowAnyException();
    }
}
