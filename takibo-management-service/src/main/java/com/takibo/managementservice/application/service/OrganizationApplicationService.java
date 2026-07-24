package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.port.OrganizationWritePort;
import com.takibo.managementservice.application.result.OrganizationResult;
import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationCreationPlan;
import com.takibo.managementservice.domain.service.OrganizationCreationDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationApplicationService {

    private final OrganizationWritePort organizationWritePort;
    private final OrganizationCreationDomainService
            organizationCreationDomainService;

    @Transactional
    public OrganizationResult create(String code, String name) {
        OrganizationCreationPlan creationPlan =
                organizationCreationDomainService.prepareCreation(code, name);
        Optional.of(creationPlan.code())
                .filter(organizationWritePort::existsByCode)
                .ifPresent(existingCode -> {
                    throw new OrganizationCodeAlreadyExistsException(
                            existingCode
                    );
                });

        return organizationWritePort.create(
                UUID.randomUUID(),
                creationPlan.code(),
                creationPlan.name(),
                creationPlan.status()
        );
    }
}
