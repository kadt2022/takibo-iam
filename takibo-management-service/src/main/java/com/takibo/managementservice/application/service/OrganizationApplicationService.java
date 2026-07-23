package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.common.TakiboCodeNormalizer;
import com.takibo.managementservice.application.port.OrganizationWritePort;
import com.takibo.managementservice.application.result.OrganizationResult;
import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationApplicationService {
  private final OrganizationWritePort organizations;

  @Transactional
  public OrganizationResult create(String code, String name) {
    String normalizedCode = TakiboCodeNormalizer.normalizeOrg(code);
    if (organizations.existsByCode(normalizedCode)) {
      throw new OrganizationCodeAlreadyExistsException(normalizedCode);
    }

    return organizations.create(UUID.randomUUID(), normalizedCode, name, OrganizationStatus.ACTIVE);
  }
}
