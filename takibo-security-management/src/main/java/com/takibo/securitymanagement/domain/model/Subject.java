package com.takibo.securitymanagement.domain.model;

import java.util.Set;

public record Subject(
        String id,
        Set<String> roles,
        Set<String> permissions,
        String orgId,
        String spaceId
) {}
