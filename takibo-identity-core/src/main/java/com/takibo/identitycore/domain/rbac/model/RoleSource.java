package com.takibo.identitycore.domain.rbac.model;

public enum RoleSource {
    /** Rôle du catalogue technique plateforme (code défini dans les enums). */
    TECHNICAL,
    /** Rôle tenant persisté en base, nature GOVERNANCE (administration locale). */
    GOVERNANCE,
    /** Rôle tenant persisté en base, nature BUSINESS (référencé par businessRoleId). */
    BUSINESS
}
