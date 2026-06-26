package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientRedirectUriEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OAuth2ClientRedirectUriRepository extends JpaRepository<OAuth2ClientRedirectUriEntity, UUID> {
    List<OAuth2ClientRedirectUriEntity> findByClientId(UUID clientId);
}
