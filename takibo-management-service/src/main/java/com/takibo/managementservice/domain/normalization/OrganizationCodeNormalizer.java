package com.takibo.managementservice.domain.normalization;

public final class OrganizationCodeNormalizer {

    public String normalize(String rawCode) {
        return TenantCodeNormalizer.normalizeOrganizationCode(rawCode);
    }
}
