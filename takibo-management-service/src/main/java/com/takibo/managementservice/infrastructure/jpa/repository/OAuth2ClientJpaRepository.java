package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.OAuth2ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OAuth2ClientJpaRepository extends JpaRepository<OAuth2ClientEntity, UUID> {
    boolean existsByClientId(String clientId);
}
