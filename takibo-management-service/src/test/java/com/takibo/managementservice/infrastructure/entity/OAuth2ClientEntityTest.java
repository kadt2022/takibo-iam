package com.takibo.managementservice.infrastructure.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ClientEntityTest {

    @Test
    void prePersist_fillsMissingColumnsWithSecureDefaults() {
        OAuth2ClientEntity entity = new OAuth2ClientEntity();
        entity.setClientType(null);
        entity.setRequireClientSecret(null);
        entity.setRequirePkce(null);
        entity.setRequireConsent(null);
        entity.setTokenEndpointAuthMethod(null);
        Instant before = Instant.now();

        entity.prePersist();

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getClientType()).isEqualTo(OAuth2ClientEntity.ClientType.CONFIDENTIAL);
        assertThat(entity.getRequireClientSecret()).isTrue();
        assertThat(entity.getRequirePkce()).isFalse();
        assertThat(entity.getRequireConsent()).isFalse();
        assertThat(entity.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(entity.getClientIdIssuedAt()).isBetween(before, Instant.now());
        assertThat(entity.getCreatedAt()).isEqualTo(entity.getClientIdIssuedAt());
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void prePersist_neverOverridesAnExplicitPublicProfile() {
        UUID id = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2030-01-01T00:00:00Z");
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder()
                .id(id)
                .clientType(OAuth2ClientEntity.ClientType.PUBLIC)
                .requireClientSecret(false)
                .requirePkce(true)
                .requireConsent(true)
                .tokenEndpointAuthMethod("none")
                .clientIdIssuedAt(issuedAt)
                .createdAt(issuedAt)
                .updatedAt(issuedAt)
                .build();

        entity.prePersist();

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getClientType()).isEqualTo(OAuth2ClientEntity.ClientType.PUBLIC);
        assertThat(entity.getRequireClientSecret()).isFalse();
        assertThat(entity.getRequirePkce()).isTrue();
        assertThat(entity.getRequireConsent()).isTrue();
        assertThat(entity.getTokenEndpointAuthMethod()).isEqualTo("none");
        assertThat(entity.getClientIdIssuedAt()).isEqualTo(issuedAt);
        assertThat(entity.getCreatedAt()).isEqualTo(issuedAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(issuedAt);
    }

    @Test
    void theBuilderStartsFromTheSameSecureDefaults() {
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder().build();

        assertThat(entity.getClientType()).isEqualTo(OAuth2ClientEntity.ClientType.CONFIDENTIAL);
        assertThat(entity.getRequireClientSecret()).isTrue();
        assertThat(entity.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(entity.getVersion()).isZero();
        assertThat(entity.getScopes()).isEmpty();
        assertThat(entity.getSecretHistory()).isEmpty();
    }

    @Test
    void identityIsCarriedByThePrimaryKeyOnly() {
        UUID id = UUID.randomUUID();
        OAuth2ClientEntity first = OAuth2ClientEntity.builder().id(id).clientId("a").build();
        OAuth2ClientEntity sameRow = OAuth2ClientEntity.builder().id(id).clientId("b").build();
        OAuth2ClientEntity otherRow = OAuth2ClientEntity.builder().id(UUID.randomUUID()).clientId("a").build();

        Object otherType = "a";
        Object self = first;

        assertThat(first)
                .isEqualTo(sameRow)
                .hasSameHashCodeAs(sameRow)
                .isNotEqualTo(otherRow)
                .isNotEqualTo(otherType);
        assertThat(first.equals(self)).as("equals est réflexif").isTrue();
    }
}
