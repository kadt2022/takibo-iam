package com.takibo.securitymanagement.domain.model;

import java.util.Set;

public record Subject(
        String id,
        Set<String> roles,
        Set<String> permissions,
        String orgId,
        String spaceId,
        String subjectType,
        String scopeLevel,
        String accountId
) {
    public Subject(String id,
                   Set<String> roles,
                   Set<String> permissions,
                   String orgId,
                   String spaceId) {
        this(id,
                roles,
                permissions,
                orgId,
                spaceId,
                "HUMAN",
                spaceId == null ? "ORGANIZATION" : "SPACE",
                "account");
    }
}
