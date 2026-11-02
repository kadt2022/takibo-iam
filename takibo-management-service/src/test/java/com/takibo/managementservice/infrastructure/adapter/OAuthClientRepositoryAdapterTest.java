package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.exception.ClientAlreadyExistsException;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void updateSecretByIdAndOrgIdAndSpaceId_usesExpectedVersionAndReturnsTrueOnlyForSingleSituatedRow() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2035-01-01T00:00:00Z");

        when(jpa.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId, 4L, "hash", expiresAt))
                .thenReturn(1, 0);

        assertThat(adapter.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId, 4L, "hash", expiresAt)).isTrue();
        assertThat(adapter.updateSecretByIdAndOrgIdAndSpaceId(clientId, orgId, spaceId, 4L, "hash", expiresAt)).isFalse();
        verify(jpa, times(2)).updateSecretByIdAndOrgIdAndSpaceId(
                clientId, orgId, spaceId, 4L, "hash", expiresAt);
        verifyNoInteractions(mapper);
    }

    @Test
    void save_when_global_client_id_constraint_wins_translates_to_domain_conflict() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID orgId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        OAuthClient client = OAuthClient.create(
                orgId, SpaceId.of(spaceId), "machine-client", "Machine Client", ClientType.CONFIDENTIAL);
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder()
                .id(client.getId().getValue())
                .orgId(orgId)
                .spaceId(spaceId)
                .clientId("machine-client")
                .clientName("Machine Client")
                .build();
        DataIntegrityViolationException databaseConflict = new DataIntegrityViolationException(
                "duplicate OAuth client",
                new IllegalStateException("unique index uq_oauth2_clients_client_id_global"));

        when(mapper.toEntity(eq(client), any())).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenThrow(databaseConflict);

        assertThatThrownBy(() -> adapter.save(client))
                .isInstanceOf(ClientAlreadyExistsException.class)
                .hasMessageContaining("machine-client")
                .hasCause(databaseConflict);
    }

    @Test
    void save_newClient_flushesBeforeReturningOneTimeSecretToApplication() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID orgId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        OAuthClient client = OAuthClient.create(
                orgId, SpaceId.of(spaceId), "machine-client", "Machine Client", ClientType.CONFIDENTIAL);
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder()
                .id(client.getId().getValue())
                .orgId(orgId)
                .spaceId(spaceId)
                .clientId("machine-client")
                .clientName("Machine Client")
                .build();

        when(mapper.toEntity(eq(client), any())).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(client);

        assertThat(adapter.save(client)).isSameAs(client);
        verify(jpa).saveAndFlush(entity);
        verify(jpa, never()).save(entity);
    }

    @Test
    void save_when_unrelated_constraint_fails_preserves_database_failure() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID orgId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        OAuthClient client = OAuthClient.create(
                orgId, SpaceId.of(spaceId), "machine-client", "Machine Client", ClientType.CONFIDENTIAL);
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder()
                .id(client.getId().getValue())
                .orgId(orgId)
                .spaceId(spaceId)
                .clientId("machine-client")
                .clientName("Machine Client")
                .build();
        DataIntegrityViolationException databaseFailure = new DataIntegrityViolationException(
                "check constraint ck_oauth2_clients_type");

        when(mapper.toEntity(eq(client), any())).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenThrow(databaseFailure);

        assertThatThrownBy(() -> adapter.save(client)).isSameAs(databaseFailure);
    }

    @Test
    void save_when_scoped_client_id_constraint_wins_translates_to_domain_conflict() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        OAuthClient client = OAuthClient.create(
                UUID.randomUUID(), null, "org-client", "Org Client", ClientType.CONFIDENTIAL);
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder().id(client.getId().getValue()).build();
        DataIntegrityViolationException databaseConflict = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_oauth2_clients_scope_client_id\"");

        when(jpa.findById(client.getId().getValue())).thenReturn(Optional.empty());
        when(mapper.toEntity(eq(client), any())).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenThrow(databaseConflict);

        assertThatThrownBy(() -> adapter.save(client))
                .isInstanceOf(ClientAlreadyExistsException.class)
                .hasMessageContaining("org-client");
    }

    @Test
    void save_existingClient_updatesTheManagedRowInsteadOfInsertingANewOne() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        OAuthClient client = OAuthClient.create(
                UUID.randomUUID(), SpaceId.of(UUID.randomUUID()), "machine-client", "Machine Client",
                ClientType.CONFIDENTIAL);
        OAuth2ClientEntity managed = OAuth2ClientEntity.builder().id(client.getId().getValue()).build();
        OAuthClient reloaded = client.withSecret("rotated", null);

        when(jpa.findById(client.getId().getValue())).thenReturn(Optional.of(managed));
        when(jpa.save(managed)).thenReturn(managed);
        when(mapper.toDomain(managed)).thenReturn(reloaded);

        assertThat(adapter.save(client)).isSameAs(reloaded);
        verify(mapper).applyDomainState(client, managed);
        verify(mapper, never()).toEntity(any(), any());
        verify(jpa, never()).saveAndFlush(any());
    }

    @Test
    void save_newSpaceClient_referencesItsSpaceThroughTheEntityManager() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID spaceId = UUID.randomUUID();
        OAuthClient client = OAuthClient.create(
                UUID.randomUUID(), SpaceId.of(spaceId), "space-client", "Space Client", ClientType.CONFIDENTIAL);
        com.takibo.managementservice.infrastructure.entity.SpaceEntity space =
                new com.takibo.managementservice.infrastructure.entity.SpaceEntity();
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder().id(client.getId().getValue()).build();

        when(jpa.findById(client.getId().getValue())).thenReturn(Optional.empty());
        when(entityManager.getReference(com.takibo.managementservice.infrastructure.entity.SpaceEntity.class, spaceId))
                .thenReturn(space);
        when(mapper.toEntity(eq(client), any())).thenAnswer(invocation -> {
            com.takibo.managementservice.infrastructure.jpa.mapper.SpaceRef ref = invocation.getArgument(1);
            assertThat(ref.getReference(spaceId)).isSameAs(space);
            return entity;
        });
        when(jpa.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(client);

        assertThat(adapter.save(client)).isSameAs(client);
    }

    @Test
    void lookupsDelegateToTheClientTable() {
        OAuthClientRepositoryAdapter adapter = new OAuthClientRepositoryAdapter(jpa, mapper, entityManager);
        UUID id = UUID.randomUUID();
        OAuth2ClientEntity entity = OAuth2ClientEntity.builder().id(id).build();
        OAuthClient domain = OAuthClient.create(null, null, "platform", "Platform", ClientType.CONFIDENTIAL);

        when(jpa.existsByClientId("platform")).thenReturn(true);
        when(jpa.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.existsByClientId("platform")).isTrue();
        assertThat(adapter.findById(id)).containsSame(domain);
    }
}
