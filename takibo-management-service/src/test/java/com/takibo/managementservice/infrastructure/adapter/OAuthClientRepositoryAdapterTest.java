package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientEntity;
import com.takibo.managementservice.infrastructure.jpa.mapper.OAuthClientJpaMapper;
import com.takibo.managementservice.infrastructure.jpa.repository.OAuth2ClientJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthClientRepositoryAdapterTest {

    @Mock private OAuth2ClientJpaRepository jpa;
    @Mock private OAuthClientJpaMapper mapper;
    @Mock private EntityManager entityManager;

    @Test
    void findByIdAndOrgIdAndSpaceId_usesSituatedJpaLookupOnly() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder()
                .id(clientId)
                .orgId(orgId)
                .spaceId(spaceId)
                .clientId("machine-client")
                .clientName("Machine Client")
                .build();
        OAuthClient domain = OAuthClient.create(orgId, SpaceId.of(spaceId),
                "machine-client", "Machine Client", ClientType.CONFIDENTIAL);

        when(jpa.findByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<OAuthClient> result = adapter.findByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId);

        assertThat(result).containsSame(domain);
        verify(jpa).findByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId);
        verify(jpa, never()).findById(clientId);
    }

    @Test
    void updateSecretByIdAndOrgIdAndSpaceId_returnsTrueOnlyForSingleSituatedRow() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2035-01-01T00:00:00Z");

        when(jpa.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId, "hash", expiresAt))
                .thenReturn(1, 0);

        assertThat(adapter.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId, "hash", expiresAt)).isTrue();
        assertThat(adapter.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId, "hash", expiresAt)).isFalse();
        verifyNoInteractions(mapper);
    }
}
