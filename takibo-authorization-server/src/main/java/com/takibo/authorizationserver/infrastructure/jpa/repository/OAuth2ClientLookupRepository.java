package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuth2ClientLookupRepository extends JpaRepository<OAuth2ClientLookupEntity, UUID> {
    Optional<OAuth2ClientLookupEntity> findByOrgIdAndSpaceIdAndClientId(UUID orgId, UUID spaceId, String clientId);

    // client_id est globalement unique en v1 (cf. migration TAS), donc résoluble seul.
    Optional<OAuth2ClientLookupEntity> findByClientId(String clientId);
}
