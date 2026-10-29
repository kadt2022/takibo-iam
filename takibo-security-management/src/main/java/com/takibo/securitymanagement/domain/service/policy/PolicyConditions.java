package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Subject;

final class PolicyConditions {

    private PolicyConditions() {
    }

    /**
     * RBAC-05/06 : une décision lit une permission située du token, jamais un nom de rôle.
     * Les permissions sont calculées à l'émission pour la frontière du token (projection
     * ORGANIZATION -> SPACE seulement) : un token SPACE ne porte jamais de P_ORG_*.
     */
    static boolean hasPermission(Subject subject, TechnicalPermission permission) {
        return subject.permissions() != null && subject.permissions().contains(permission.code());
    }

    static boolean isSpaceScoped(Subject subject) {
        return "SPACE".equals(subject.scopeLevel()) && subject.spaceId() != null;
    }

    static boolean isUuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean sameUuid(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        try {
            return java.util.UUID.fromString(expected).equals(java.util.UUID.fromString(actual));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
