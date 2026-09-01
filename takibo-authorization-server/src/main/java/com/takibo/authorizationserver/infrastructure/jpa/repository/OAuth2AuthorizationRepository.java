package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Chaque {@code find...Hash} correspond à un index d'unicité global de V202608290001 : aucun
 * paramètre de tenant, {@code findByToken(...)} de Spring Authorization Server n'en fournit
 * pas non plus (TAS-GRANTS-02).
 */
public interface OAuth2AuthorizationRepository extends JpaRepository<OAuth2AuthorizationEntity, UUID> {

    Optional<OAuth2AuthorizationEntity> findByAuthorizationCodeHash(String hash);

    Optional<OAuth2AuthorizationEntity> findByAccessTokenHash(String hash);

    Optional<OAuth2AuthorizationEntity> findByOidcIdTokenHash(String hash);

    Optional<OAuth2AuthorizationEntity> findByRefreshTokenHash(String hash);

    Optional<OAuth2AuthorizationEntity> findByUserCodeHash(String hash);

    Optional<OAuth2AuthorizationEntity> findByDeviceCodeHash(String hash);

    Optional<OAuth2AuthorizationEntity> findByState(String state);
}
