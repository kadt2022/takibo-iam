package com.takibo.authorizationserver.infrastructure.jpa.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ClientLookupEntityTest {

    @Test
    void exposes_persisted_jwk_configuration() {
        OAuth2ClientLookupEntity entity = new OAuth2ClientLookupEntity();
        ReflectionTestUtils.setField(entity, "jwksUri", "https://keys.example/jwks.json");
        ReflectionTestUtils.setField(entity, "jwksJson", "{\"keys\":[]}");
        ReflectionTestUtils.setField(entity, "idTokenSignedAlg", "RS256");

        assertThat(entity.getJwksUri()).isEqualTo("https://keys.example/jwks.json");
        assertThat(entity.getJwksJson()).isEqualTo("{\"keys\":[]}");
        assertThat(entity.getIdTokenSignedAlg()).isEqualTo("RS256");
    }
}
