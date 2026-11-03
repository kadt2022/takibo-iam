package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.IdentityStatus;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import com.takibo.identitycore.infrastructure.entity.TakiboIdentityEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TakiboIdentityJpaMapperTest {

    private static final UUID IDENTITY_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ACTIVE_AT = Instant.parse("2026-03-01T00:00:00Z");

    private final TakiboIdentityJpaMapper mapper = Mappers.getMapper(TakiboIdentityJpaMapper.class);

    @Test
    void toEntity_unwrapsTheOrganizationAnchorAndKeepsEveryAttribute() {
        TakiboIdentity identity = TakiboIdentity.builder()
                .id(TakiboIdentityId.of(IDENTITY_UUID))
                .orgId(OrganizationId.of(ORG_UUID))
                .accountId(AccountId.of(ACCOUNT_UUID))
                .identityType(IdentityType.SERVICE)
                .identityStatus(IdentityStatus.SUSPENDED)
                .trustLevel(2).riskScore(40)
                .lastActiveAt(ACTIVE_AT).createdAt(CREATED).updatedAt(CREATED)
                .createdBy("SYSTEM").version(6L)
                .build();

        TakiboIdentityEntity entity = mapper.toEntity(identity);

        assertThat(entity.getIdentityId()).isEqualTo(IDENTITY_UUID);
        assertThat(entity.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(entity.getAccountId()).isEqualTo(ACCOUNT_UUID);
        assertThat(entity.getIdentityType()).isEqualTo(IdentityType.SERVICE);
        assertThat(entity.getIdentityStatus()).isEqualTo(IdentityStatus.SUSPENDED);
        assertThat(entity.getTrustLevel()).isEqualTo(2);
        assertThat(entity.getRiskScore()).isEqualTo(40);
        assertThat(entity.getLastActiveAt()).isEqualTo(ACTIVE_AT);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED);
        assertThat(entity.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(entity.getVersion()).isEqualTo(6L);
    }

    @Test
    void roundTrip_preservesTheIdentity() {
        TakiboIdentity human = TakiboIdentity.createHuman(
                TakiboIdentityId.of(IDENTITY_UUID), OrganizationId.of(ORG_UUID), AccountId.of(ACCOUNT_UUID));

        TakiboIdentity back = mapper.toDomain(mapper.toEntity(human));

        assertThat(back).isEqualTo(human);
        assertThat(back.getOrgId()).isEqualTo(OrganizationId.of(ORG_UUID));
        assertThat(back.getAccountId()).isEqualTo(AccountId.of(ACCOUNT_UUID));
        assertThat(back.getIdentityType()).isEqualTo(IdentityType.HUMAN);
        assertThat(back.getIdentityStatus()).isEqualTo(IdentityStatus.ACTIVE);
    }

    @Test
    void nullsAreTolerated() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        TakiboIdentity orphan = mapper.toDomain(new TakiboIdentityEntity());
        assertThat(orphan.getId()).isNull();
        assertThat(orphan.getOrgId()).isNull();
        assertThat(orphan.getAccountId()).isNull();
    }
}
