package com.takibo.managementservice.infrastructure.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceDomainEntityTest {

    @Test
    void aNewDomainIsUnverifiedUntilProven() {
        SpaceDomainEntity domain = new SpaceDomainEntity();
        domain.setVerified(null);

        domain.prePersist();

        assertThat(domain.getId()).isNotNull();
        assertThat(domain.getVerified()).isFalse();
        assertThat(domain.getCreatedAt()).isNotNull();
        assertThat(domain.getUpdatedAt()).isEqualTo(domain.getCreatedAt());
    }

    @Test
    void prePersist_keepsExplicitValues() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2030-01-01T00:00:00Z");
        SpaceDomainEntity domain = SpaceDomainEntity.builder()
                .id(id).verified(true).createdAt(at).updatedAt(at).build();

        domain.prePersist();

        assertThat(domain.getId()).isEqualTo(id);
        assertThat(domain.getVerified()).isTrue();
        assertThat(domain.getCreatedAt()).isEqualTo(at);
        assertThat(domain.getUpdatedAt()).isEqualTo(at);
    }

    @Test
    void generatingAVerificationTokenRevokesAPreviousVerification() {
        SpaceDomainEntity domain = SpaceDomainEntity.builder().verified(true).build();

        domain.generateVerificationToken();

        assertThat(domain.getVerified()).isFalse();
        assertThat(domain.getVerificationToken()).isNotBlank();
        assertThat(UUID.fromString(domain.getVerificationToken())).isNotNull();
    }

    @Test
    void eachVerificationTokenIsFresh() {
        SpaceDomainEntity domain = new SpaceDomainEntity();

        domain.generateVerificationToken();
        String first = domain.getVerificationToken();
        domain.generateVerificationToken();

        assertThat(domain.getVerificationToken()).isNotEqualTo(first);
    }

    @Test
    void verifyingConsumesTheVerificationToken() {
        SpaceDomainEntity domain = new SpaceDomainEntity();
        domain.generateVerificationToken();

        domain.verify();

        assertThat(domain.getVerified()).isTrue();
        assertThat(domain.getVerificationToken()).isNull();
    }

    @Test
    void identityIsCarriedByThePrimaryKeyOnly() {
        UUID id = UUID.randomUUID();
        SpaceDomainEntity first = SpaceDomainEntity.builder().id(id).domain("a.example").build();

        Object otherType = "a.example";
        Object self = first;

        assertThat(first)
                .isEqualTo(SpaceDomainEntity.builder().id(id).domain("b.example").build())
                .hasSameHashCodeAs(SpaceDomainEntity.builder().id(id).build())
                .isNotEqualTo(SpaceDomainEntity.builder().id(UUID.randomUUID()).build())
                .isNotEqualTo(otherType);
        assertThat(first.equals(self)).as("equals est réflexif").isTrue();
    }
}
