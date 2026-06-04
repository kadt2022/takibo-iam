package com.takibo.identitycore.domain.catalogrbac;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.*;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.DELETE_ORG;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalScope.*;


public enum TechnicalRole {

    SYSTEM_ADMIN(
            "R_TAKIBO_PLATFORM_ADMIN",
            SYSTEM,
            Set.of(
                    CREATE_ORG,
                    DELETE_ORG,
                    READ_ORG,
                    UPDATE_ORG_SETTINGS,
                    CREATE_SPACE,
                    DELETE_SPACE,
                    MANAGE_USERS,
                    MANAGE_CLIENTS,
                    ASSIGN_ROLES,
                    READ_AUDIT_LOGS,
                    EXPORT_AUDIT_LOGS,
                    READ_POLICY,
                    UPDATE_POLICY
            )
    ),

    SYSTEM_AUDITOR(
            "R_TAKIBO_PLATFORM_AUDITOR",
            SYSTEM,
            Set.of(
                    READ_ORG,
                    READ_AUDIT_LOGS,
                    READ_POLICY
            )
    ),

    ORG_OWNER(
            "R_ORG_OWNER",
            ORGANIZATION,
            Set.of(
                    READ_ORG,
                    UPDATE_ORG_SETTINGS,
                    CREATE_SPACE,
                    DELETE_SPACE,
                    MANAGE_USERS,
                    MANAGE_CLIENTS,
                    ASSIGN_ROLES,
                    READ_AUDIT_LOGS,
                    EXPORT_AUDIT_LOGS,
                    READ_POLICY,
                    UPDATE_POLICY
            )
    ),

    ORG_ADMIN(
            "R_ORG_ADMIN",
            ORGANIZATION,
            Set.of(
                    READ_ORG,
                    UPDATE_ORG_SETTINGS,
                    CREATE_SPACE,
                    DELETE_SPACE,
                    MANAGE_USERS,
                    MANAGE_CLIENTS,
                    ASSIGN_ROLES,
                    READ_AUDIT_LOGS,
                    READ_POLICY,
                    UPDATE_POLICY
            )
    ),

    ORG_USER_ADMIN(
            "R_ORG_USER_ADMIN",
            ORGANIZATION,
            Set.of(
                    MANAGE_USERS
            )
    ),

    ORG_CLIENT_ADMIN(
            "R_ORG_CLIENT_ADMIN",
            ORGANIZATION,
            Set.of(
                    MANAGE_CLIENTS
            )
    ),

    ORG_AUDITOR(
            "R_ORG_AUDITOR",
            ORGANIZATION,
            Set.of(
                    READ_ORG,
                    READ_AUDIT_LOGS,
                    EXPORT_AUDIT_LOGS,
                    READ_POLICY
            )
    ),

    ORG_VIEWER(
            "R_ORG_VIEWER",
            ORGANIZATION,
            Set.of(
                    READ_ORG,
                    READ_POLICY
            )
    ),

    SPACE_ADMIN(
            "R_SPACE_ADMIN",
            SPACE,
            Set.of(
                    MANAGE_USERS,
                    MANAGE_CLIENTS,
                    ASSIGN_ROLES,
                    READ_AUDIT_LOGS,
                    READ_POLICY,
                    UPDATE_POLICY
            )
    ),

    SPACE_USER_ADMIN(
            "R_SPACE_USER_ADMIN",
            SPACE,
            Set.of(
                    MANAGE_USERS
            )
    ),

    SPACE_CLIENT_ADMIN(
            "R_SPACE_CLIENT_ADMIN",
            SPACE,
            Set.of(
                    MANAGE_CLIENTS
            )
    ),

    SPACE_VIEWER(
            "R_SPACE_VIEWER",
            SPACE,
            Set.of(
                    READ_AUDIT_LOGS,
                    READ_POLICY
            )
    ),

    SELF(
            "R_SELF",
            USER,
            Set.of(
                    READ_AUDIT_LOGS
            )
    );

    private final String code;
    private final TechnicalScope scope;
    private final Set<TechnicalGroup.TechnicalPermission> permissions;

    TechnicalRole(String code, TechnicalScope scope, Set<TechnicalGroup.TechnicalPermission> permissions) {
        this.code = code;
        this.scope = scope;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public String code() {
        return code;
    }

    public TechnicalScope scope() {
        return scope;
    }

    public Set<TechnicalGroup.TechnicalPermission> permissions() {
        return permissions;
    }

    public static Optional<TechnicalRole> fromCode(String code) {
        return Arrays.stream(values())
                .filter(r -> r.code.equals(code))
                .findFirst();
    }
}
