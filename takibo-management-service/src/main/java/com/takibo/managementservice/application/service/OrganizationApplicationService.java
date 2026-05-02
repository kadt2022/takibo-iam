package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateOrganizationCommand;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaOrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationApplicationService {
  private final JpaOrganizationRepository organizations;

  @Transactional
  public CreateOrganizationCommand create(String code, String name) {
    var e = new OrganizationEntity();
    e.setId(UUID.randomUUID());
    e.setCode(code);
    e.setName(name);
    e.setStatus(OrganizationStatus.ACTIVE);
    organizations.saveAndFlush(e);
    return new CreateOrganizationCommand(e.getId(), e.getCode(), e.getName());
  }
}
