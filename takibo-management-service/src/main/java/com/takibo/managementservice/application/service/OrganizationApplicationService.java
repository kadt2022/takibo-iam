package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateOrganizationCommand;
import com.takibo.managementservice.application.common.TakiboCodeNormalizer;
import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.jpa.support.DatabaseConstraintViolation;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaOrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationApplicationService {
  private static final String ORGANIZATION_CODE_UNIQUE_INDEX = "uk_organizations_code_ci";
  private static final String LEGACY_ORGANIZATION_CODE_UNIQUE_CONSTRAINT = "uk_organizations_code";

  private final JpaOrganizationRepository organizations;

  @Transactional
  public CreateOrganizationCommand create(String code, String name) {
    String normalizedCode = TakiboCodeNormalizer.normalizeOrg(code);
    if (organizations.existsByCode(normalizedCode)) {
      throw new OrganizationCodeAlreadyExistsException(normalizedCode);
    }

    var e = new OrganizationEntity();
    e.setId(UUID.randomUUID());
    e.setCode(normalizedCode);
    e.setName(name);
    e.setStatus(OrganizationStatus.ACTIVE);
    try {
      organizations.saveAndFlush(e);
    } catch (DataIntegrityViolationException failure) {
      if (DatabaseConstraintViolation.mentions(
              failure,
              ORGANIZATION_CODE_UNIQUE_INDEX,
              LEGACY_ORGANIZATION_CODE_UNIQUE_CONSTRAINT)) {
        throw new OrganizationCodeAlreadyExistsException(normalizedCode, failure);
      }
      throw failure;
    }
    return new CreateOrganizationCommand(e.getId(), e.getCode(), e.getName());
  }
}
