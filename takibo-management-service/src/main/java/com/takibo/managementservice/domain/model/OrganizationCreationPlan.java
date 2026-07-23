package com.takibo.managementservice.domain.model;

public record OrganizationCreationPlan(
        String code,
        String name,
        OrganizationStatus status
) {
}
