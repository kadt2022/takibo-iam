package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.normalization.TenantCodeNormalizer;

import java.util.Optional;
import java.util.function.Predicate;

public final class SpaceCodePolicy {

    private static final int MAXIMUM_BASE_LENGTH = 30;

    public String resolveBaseCode(String requestedCode, String spaceName) {
        return Optional.ofNullable(requestedCode)
                .filter(Predicate.not(String::isBlank))
                .orElse(spaceName);
    }

    public String normalizeCandidateCode(String baseCode, int suffix) {
        String normalizedCode = TenantCodeNormalizer.normalize(baseCode);
        String boundedCode = Optional.of(normalizedCode)
                .filter(value -> value.length() <= MAXIMUM_BASE_LENGTH)
                .orElseGet(() -> normalizedCode
                        .substring(0, MAXIMUM_BASE_LENGTH)
                        .replaceAll("-+$", "")
                );
        return TenantCodeNormalizer.normalizeSpaceCode(boundedCode, suffix);
    }
}
