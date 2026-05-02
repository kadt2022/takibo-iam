package com.takibo.managementservice.domain.repository;

import com.takibo.managementservice.domain.model.OAuthClient;

import java.util.Optional;
import java.util.UUID;

public interface OAuthClientRepository {
    boolean existsByClientId(String clientId);
    OAuthClient save(OAuthClient client);
    Optional<OAuthClient> findById(UUID id);
}
