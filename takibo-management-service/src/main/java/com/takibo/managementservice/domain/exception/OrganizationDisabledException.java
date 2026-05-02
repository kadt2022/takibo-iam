package com.takibo.managementservice.domain.exception;

import java.util.UUID;

public class OrganizationDisabledException extends RuntimeException {
    public OrganizationDisabledException(UUID orgId) {
        super("Organization is disabled: " + orgId);
    }
}
