package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientGrantTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OAuth2ClientGrantTypeRepository extends JpaRepository<OAuth2ClientGrantTypeEntity, UUID> {
    List<OAuth2ClientGrantTypeEntity> findByClientId(UUID clientId);
}
