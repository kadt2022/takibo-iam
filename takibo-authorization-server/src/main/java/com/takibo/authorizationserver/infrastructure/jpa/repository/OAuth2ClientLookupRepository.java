package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuth2ClientLookupRepository extends JpaRepository<OAuth2ClientLookupEntity, UUID> {

    // client_id est globalement unique en v1 (cf. migration TAS), donc résoluble seul —
    // c'est ce qui rend ResolvedOAuthClientResolver possible sans connaître org_id/space_id
    // au préalable (TAS-GRANTS-01). findByOrgIdAndSpaceIdAndClientId a disparu avec le
    // dernier appelant qui en dépendait, PkceEnforcementFilter.
    Optional<OAuth2ClientLookupEntity> findByClientId(String clientId);
}
