package com.takibo.identitycore.infrastructure.entity;

import com.takibo.identitycore.domain.model.IdentityStatus;
import com.takibo.identitycore.domain.model.IdentityType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TakiboIdentityEntityTest {

    private static final UUID IDENTITY_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Test
    void prePersist_fillsMissingDefaults_failSafe() {
        TakiboIdentityEntity entity = new TakiboIdentityEntity();
        entity.setIdentityStatus(null);
        entity.setTrustLevel(null);
        entity.setRiskScore(null);

        entity.prePersist();

        assertThat(entity.getIdentityId()).isNotNull();
        assertThat(entity.getIdentityType()).isEqualTo(IdentityType.HUMAN);
        assertThat(entity.getIdentityStatus()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(entity.getTrustLevel()).isZero();
        assertThat(entity.getRiskScore()).isZero();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void prePersist_neverOverridesProvidedValues() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        TakiboIdentityEntity entity = TakiboIdentityEntity.builder()
                .identityId(IDENTITY_UUID)
                .identityType(IdentityType.MACHINE)
                .identityStatus(IdentityStatus.DISABLED)
                .trustLevel(3).riskScore(70)
                .createdAt(created).updatedAt(created)
                .build();

        entity.prePersist();

        assertThat(entity.getIdentityId()).isEqualTo(IDENTITY_UUID);
        assertThat(entity.getIdentityType()).isEqualTo(IdentityType.MACHINE);
        assertThat(entity.getIdentityStatus()).isEqualTo(IdentityStatus.DISABLED);
        assertThat(entity.getTrustLevel()).isEqualTo(3);
        assertThat(entity.getRiskScore()).isEqualTo(70);
        assertThat(entity.getCreatedAt()).isEqualTo(created);
        assertThat(entity.getUpdatedAt()).isEqualTo(created);
    }

    @Test
    void equality_isCarriedByTheIdentityIdentifier() {
        TakiboIdentityEntity a = TakiboIdentityEntity.builder().identityId(IDENTITY_UUID).riskScore(1).build();
        TakiboIdentityEntity b = TakiboIdentityEntity.builder().identityId(IDENTITY_UUID).riskScore(99).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isEqualTo(a);
        assertThat(a).isNotEqualTo(TakiboIdentityEntity.builder().identityId(UUID.randomUUID()).build());
        assertThat(a).isNotEqualTo("not an entity");
    }
}
