package com.takibo.managementservice.infrastructure.jpa.repository;

import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, UUID> {
}
