package com.takibo.securitymanagement.domain.model;

public record Resource(
        String path,
        String orgId,
        String spaceId
) {}
