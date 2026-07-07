package com.takibo.identitycore.domain.rbac.model;

public enum GroupSource {
    /** Groupe du catalogue technique plateforme (code défini dans les enums). */
    TECHNICAL,
    /** Groupe tenant persisté en base, nature GOVERNANCE (administration locale). */
    GOVERNANCE,
    /** Groupe tenant persisté en base, nature BUSINESS (référencé par businessGroupId). */
    BUSINESS
}