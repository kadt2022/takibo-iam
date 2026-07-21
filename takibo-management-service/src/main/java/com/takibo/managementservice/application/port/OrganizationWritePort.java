package com.takibo.managementservice.application.port;

import com.takibo.managementservice.application.result.OrganizationResult;
import com.takibo.managementservice.domain.model.OrganizationStatus;

import java.util.UUID;

public interface OrganizationWritePort {

    boolean existsByCode(String code);

    OrganizationResult create(UUID id, String code, String name, OrganizationStatus status);
}
