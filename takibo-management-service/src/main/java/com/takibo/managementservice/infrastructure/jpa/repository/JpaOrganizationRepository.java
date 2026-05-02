package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaOrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
  Optional<OrganizationEntity> findByCode(String code);
  boolean existsByCode(String code);
}
