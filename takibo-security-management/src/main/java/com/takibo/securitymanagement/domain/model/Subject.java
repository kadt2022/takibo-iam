package com.takibo.securitymanagement.domain.model;

import java.util.Set;

/**
 * Sujet vu par le {@link com.takibo.securitymanagement.domain.service.PolicyEvaluator}.
 * <p>
 * {@code subjectType} porte la nature du sujet telle qu'émise par le token
 * ({@code HUMAN} / {@code SERVICE} / {@code SYSTEM}). Les règles qui exigent un humain
 * doivent tester {@link #isHuman()} — un type absent ou inconnu n'est jamais humain
 * (fail-closed). {@code scopeLevel} et {@code accountId} portent la situation du token
 * (surface {@code /me/spaces}).
 */
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

    public static final String TYPE_HUMAN = "HUMAN";

    /**
     * Constructeur de compatibilité : dérive une situation par défaut à partir de
     * l'orgId/spaceId (utile aux appelants/tests qui ne portent pas encore la
     * nature ni la portée explicites).
     */
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
                TYPE_HUMAN,
                spaceId == null ? "ORGANIZATION" : "SPACE",
                "account");
    }

    public boolean isHuman() {
        return TYPE_HUMAN.equals(subjectType);
    }
}
