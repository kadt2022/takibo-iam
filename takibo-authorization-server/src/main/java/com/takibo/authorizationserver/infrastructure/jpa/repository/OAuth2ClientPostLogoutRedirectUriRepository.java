package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientPostLogoutRedirectUriEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OAuth2ClientPostLogoutRedirectUriRepository
        extends JpaRepository<OAuth2ClientPostLogoutRedirectUriEntity, UUID> {
    List<OAuth2ClientPostLogoutRedirectUriEntity> findByClientId(UUID clientId);
}
