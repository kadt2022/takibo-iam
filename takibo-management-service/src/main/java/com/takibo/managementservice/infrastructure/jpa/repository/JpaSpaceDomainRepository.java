package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.SpaceDomainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaSpaceDomainRepository extends JpaRepository<SpaceDomainEntity, UUID> {
  Optional<SpaceDomainEntity> findByDomainAndVerifiedTrue(String domain);
}
