package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientScopeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OAuth2ClientScopeRepository extends JpaRepository<OAuth2ClientScopeEntity, UUID> {
    List<OAuth2ClientScopeEntity> findByClientId(UUID clientId);
}
