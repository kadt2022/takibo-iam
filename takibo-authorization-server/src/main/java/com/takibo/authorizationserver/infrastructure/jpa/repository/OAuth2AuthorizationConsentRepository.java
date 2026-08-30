package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code findByRegisteredClientIdAndPrincipalName} correspond à l'index d'unicité global de
 * V202608290001 : {@code OAuth2AuthorizationConsentService.findById(registeredClientId,
 * principalName)} n'a aucun paramètre de tenant (TAS-GRANTS-02).
 */
public interface OAuth2AuthorizationConsentRepository
        extends JpaRepository<OAuth2AuthorizationConsentEntity, UUID> {

    Optional<OAuth2AuthorizationConsentEntity> findByRegisteredClientIdAndPrincipalName(
            String registeredClientId, String principalName);
}
