package com.takibo.managementservice.interfaces.rest.request;

import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRegistrationRequestTest {

    @Test
    void toString_never_exposes_embedded_jwks() {
        ClientRegistrationRequest request = new ClientRegistrationRequest(
                "secure-client",
                "Secure Client",
                ClientType.CONFIDENTIAL,
                false,
                TokenEndpointAuthMethod.private_key_jwt,
                false,
                false,
                null,
                "{\"keys\":[{\"d\":\"private-material\"}]}",
                "RS256",
                900,
                3600,
                900,
                null,
                Set.of("api:read"),
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of(),
                Set.of("https://app.example")
        );

        assertThat(request.toString())
                .contains("jwksJson=********")
                .doesNotContain("private-material");
    }
}
