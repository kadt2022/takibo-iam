package com.takibo.managementservice.domain.repository;

import com.takibo.managementservice.domain.model.OAuthClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OAuthClientRepository {
    boolean existsByClientId(String clientId);
    OAuthClient save(OAuthClient client);
    Optional<OAuthClient> findById(UUID id);
    Optional<OAuthClient> findByIdAndOrgIdAndSpaceId(UUID id, UUID orgId, UUID spaceId);
    boolean updateSecretByIdAndOrgIdAndSpaceId(UUID id, UUID orgId, UUID spaceId, String secretHash, Instant expiresAt);
}
