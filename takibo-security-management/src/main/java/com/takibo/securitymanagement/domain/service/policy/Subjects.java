package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

/** Prédicats sur le sujet d'un token, partagés par les règles de surface. */
final class Subjects {

    static final String SCOPE_SPACE = "SPACE";
    static final String SCOPE_ORGANIZATION = "ORGANIZATION";

    private Subjects() {
    }

    /**
     * RBAC-05/06 : une décision lit une permission située du token, jamais un nom de rôle.
     * Les permissions sont calculées à l'émission pour la frontière du token (projection
     * ORGANIZATION -> SPACE seulement) : un token SPACE ne porte jamais de P_ORG_*.
     */
    static boolean hasPermission(Subject subject, TechnicalPermission permission) {
        return Optional.ofNullable(subject.permissions())
                .map(permissions -> permissions.contains(permission.code()))
                .orElse(false);
    }

    static boolean isSpaceScoped(Subject subject) {
        return SCOPE_SPACE.equals(subject.scopeLevel()) && subject.spaceId() != null;
    }

    static boolean isOrganizationScoped(Subject subject) {
        return SCOPE_ORGANIZATION.equals(subject.scopeLevel());
    }

    static boolean isOrganizationHuman(Subject subject) {
        return subject.isHuman() && isOrganizationScoped(subject);
    }

    /** Identifiant du token égal, à la casse près, à celui du chemin ; un token sans valeur ne l'est jamais. */
    static boolean sameIgnoringCase(String tokenValue, String pathValue) {
        return tokenValue != null && tokenValue.equalsIgnoreCase(pathValue);
    }

    static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
