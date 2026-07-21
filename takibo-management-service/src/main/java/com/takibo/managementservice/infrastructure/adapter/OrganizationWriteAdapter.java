package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.application.port.OrganizationWritePort;
import com.takibo.managementservice.application.result.OrganizationResult;
import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaOrganizationRepository;
import com.takibo.managementservice.infrastructure.jpa.support.DatabaseConstraintViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrganizationWriteAdapter implements OrganizationWritePort {

    private static final String ORGANIZATION_CODE_UNIQUE_INDEX = "uk_organizations_code_ci";
    private static final String LEGACY_ORGANIZATION_CODE_UNIQUE_CONSTRAINT = "uk_organizations_code";

    private final JpaOrganizationRepository organizations;

    @Override
    public boolean existsByCode(String code) {
        return organizations.existsByCode(code);
    }

    @Override
    public OrganizationResult create(UUID id, String code, String name, OrganizationStatus status) {
        OrganizationEntity organization = OrganizationEntity.builder()
                .id(id)
                .code(code)
                .name(name)
                .status(status)
                .build();
        try {
            OrganizationEntity saved = organizations.saveAndFlush(organization);
            return new OrganizationResult(saved.getId(), saved.getCode(), saved.getName());
        } catch (DataIntegrityViolationException failure) {
            if (DatabaseConstraintViolation.mentions(
                    failure,
                    ORGANIZATION_CODE_UNIQUE_INDEX,
                    LEGACY_ORGANIZATION_CODE_UNIQUE_CONSTRAINT)) {
                throw new OrganizationCodeAlreadyExistsException(code, failure);
            }
            throw failure;
        }
    }
}
