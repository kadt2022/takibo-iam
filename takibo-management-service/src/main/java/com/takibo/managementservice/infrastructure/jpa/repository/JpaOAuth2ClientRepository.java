package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.OAuth2ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaOAuth2ClientRepository extends JpaRepository<OAuth2ClientEntity, UUID> {
  Optional<OAuth2ClientEntity> findByClientId(String clientId);
  boolean existsByClientId(String clientId);
}
