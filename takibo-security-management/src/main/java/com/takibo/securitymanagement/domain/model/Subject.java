package com.takibo.securitymanagement.domain.model;

import java.util.Set;

/**
 * Sujet vu par le {@link com.takibo.securitymanagement.domain.service.PolicyEvaluator}.
 * <p>
 * {@code subjectType} porte la nature du sujet telle qu'émise par le token
 * ({@code HUMAN} / {@code SERVICE} / {@code SYSTEM}). Les règles qui exigent un humain
 * doivent tester {@link #isHuman()} — un type absent ou inconnu n'est jamais humain
 * (fail-closed).
 */
public record Subject(
        String id,
        String subjectType,
        Set<String> roles,
        Set<String> permissions,
        String orgId,
        String spaceId
) {

    public static final String TYPE_HUMAN = "HUMAN";

    public boolean isHuman() {
        return TYPE_HUMAN.equals(subjectType);
    }
}
