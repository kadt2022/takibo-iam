package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.repository.OAuthClientRepository;
import com.takibo.managementservice.infrastructure.entity.OAuth2ClientEntity;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.mapper.OAuthClientJpaMapper;
import com.takibo.managementservice.infrastructure.jpa.mapper.SpaceRef;
import com.takibo.managementservice.infrastructure.jpa.repository.OAuth2ClientJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OAuthClientRepositoryAdapter implements OAuthClientRepository {

    private final OAuth2ClientJpaRepository jpa;
    private final OAuthClientJpaMapper mapper;

    @PersistenceContext
    private final EntityManager em;

    private SpaceRef spaceRef() { return id -> em.getReference(SpaceEntity.class, id); }

    @Override public boolean existsByClientId(String clientId) { return jpa.existsByClientId(clientId); }

    @Override public OAuthClient save(OAuthClient client) {
        OAuth2ClientEntity entity = mapper.toEntity(client, spaceRef());
        OAuth2ClientEntity saved  = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override public Optional<OAuthClient> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }
}
