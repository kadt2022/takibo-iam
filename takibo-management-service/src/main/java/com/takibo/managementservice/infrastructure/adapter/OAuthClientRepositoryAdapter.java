package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.exception.ClientAlreadyExistsException;
import com.takibo.managementservice.domain.repository.OAuthClientRepository;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientEntity;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.mapper.OAuthClientJpaMapper;
import com.takibo.managementservice.infrastructure.jpa.mapper.SpaceRef;
import com.takibo.managementservice.infrastructure.jpa.repository.OAuth2ClientJpaRepository;
import com.takibo.managementservice.infrastructure.jpa.support.DatabaseConstraintViolation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OAuthClientRepositoryAdapter implements OAuthClientRepository {

    private static final String GLOBAL_CLIENT_ID_UNIQUE_INDEX = "uq_oauth2_clients_client_id_global";
    private static final String SCOPED_CLIENT_ID_UNIQUE_CONSTRAINT = "uq_oauth2_clients_scope_client_id";

    private final OAuth2ClientJpaRepository jpa;
    private final OAuthClientJpaMapper mapper;

    @PersistenceContext
    private final EntityManager em;

    private SpaceRef spaceRef() { return id -> em.getReference(SpaceEntity.class, id); }

    @Override public boolean existsByClientId(String clientId) { return jpa.existsByClientId(clientId); }

    @Override public OAuthClient save(OAuthClient client) {
        // Update (ex. rotation de secret) : appliquer l'état domaine sur l'entité
        // MANAGÉE — re-mapper via toEntity fabriquerait des enfants neufs et
        // ré-insérerait des doublons (violation d'unicité, ex. uk_ocg_client_grant).
        UUID id = client.getId() == null ? null : client.getId().getValue();
        if (id != null) {
            Optional<OAuth2ClientEntity> managed = jpa.findById(id);
            if (managed.isPresent()) {
                mapper.applyDomainState(client, managed.get());
                return mapper.toDomain(jpa.save(managed.get()));
            }
        }

        OAuth2ClientEntity entity = mapper.toEntity(client, spaceRef());
        try {
            OAuth2ClientEntity saved = jpa.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException failure) {
            if (DatabaseConstraintViolation.mentions(
                    failure,
                    GLOBAL_CLIENT_ID_UNIQUE_INDEX,
                    SCOPED_CLIENT_ID_UNIQUE_CONSTRAINT)) {
                throw new ClientAlreadyExistsException(client.getClientId(), failure);
            }
            throw failure;
        }
    }

    @Override public Optional<OAuthClient> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override public Optional<OAuthClient> findByIdAndOrgIdAndSpaceId(UUID id, UUID orgId, UUID spaceId) {
        return jpa.findByIdAndOrgIdAndSpaceId(id, orgId, spaceId).map(mapper::toDomain);
    }

    @Override
    public boolean updateSecretByIdAndOrgIdAndSpaceId(UUID id,
                                                      UUID orgId,
                                                      UUID spaceId,
                                                      Long expectedVersion,
                                                      String secretHash,
                                                      Instant expiresAt) {
        return jpa.updateSecretByIdAndOrgIdAndSpaceId(
                id, orgId, spaceId, expectedVersion, secretHash, expiresAt) == 1;
    }
}
