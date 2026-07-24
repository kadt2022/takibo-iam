package com.takibo.managementservice.domain.normalization;

import java.util.Optional;

public final class TenantCodeNormalizer {

    private static final int MINIMUM_CODE_LENGTH = 3;

    private TenantCodeNormalizer() {
    }

    public static String normalize(String rawCode) {
        return SlugNormalizer.normalize(rawCode);
    }

    public static String normalizeOrganizationCode(String rawCode) {
        return Optional.of(normalize(rawCode))
                .filter(code -> code.length() >= MINIMUM_CODE_LENGTH)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization code must contain at least three "
                                + "characters after normalization"
                ));
    }

    public static String normalizeSpaceCode(String rawCode, int suffix) {
        String normalizedCode = normalize(rawCode);
        return Optional.of(normalizedCode)
                .filter(code -> code.length() >= MINIMUM_CODE_LENGTH)
                .orElseGet(() -> normalizedCode.isBlank()
                        ? "space-" + suffix
                        : normalizedCode + "-" + suffix
                );
    }
}
