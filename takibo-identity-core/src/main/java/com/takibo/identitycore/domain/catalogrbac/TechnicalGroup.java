package com.takibo.identitycore.domain.catalogrbac;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static com.takibo.identitycore.domain.catalogrbac.TechnicalRole.*;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.ORGANIZATION;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.SPACE;

public enum TechnicalGroup {

    ORG_ADMINS(
            "G_ORG_ADMINS",
            ORGANIZATION,
            Set.of(
                    ORG_ADMIN
            )
    ),

    ORG_USERS(
            "G_ORG_USERS",
            ORGANIZATION,
            Set.of()
    ),

    ORG_CLIENTS(
            "G_ORG_CLIENTS",
            ORGANIZATION,
            Set.of()
    ),

    SPACE_ADMINS(
            "G_SPACE_ADMINS",
            SPACE,
            Set.of(
                    SPACE_ADMIN
            )
    ),

    SPACE_USERS(
            "G_SPACE_USERS",
            SPACE,
            Set.of()
    ),

    SPACE_CLIENTS(
            "G_SPACE_CLIENTS",
            SPACE,
            Set.of()
    );

    private final String code;
    private final AuthorityPlan plan;
    private final Set<TechnicalRole> roles;

    TechnicalGroup(String code, AuthorityPlan plan, Set<TechnicalRole> roles) {
        this.code = code;
        this.plan = plan;
        this.roles = Collections.unmodifiableSet(roles);
    }

    public String code() {
        return code;
    }

    public AuthorityPlan plan() {
        return plan;
    }

    public Set<TechnicalRole> roles() {
        return roles;
    }

    public static Optional<TechnicalGroup> fromCode(String code) {
        return Arrays.stream(values())
                .filter(g -> g.code.equals(code))
                .findFirst();
    }

    public enum TechnicalPermission {

        CREATE_ORG(
                "P_CREATE_ORG",
                AuthorityPlan.PLATFORM,
                "Create a new organization"
        ),
        DELETE_ORG(
                "P_DELETE_ORG",
                AuthorityPlan.PLATFORM,
                "Delete an existing organization"
        ),
        READ_ORG(
                "P_READ_ORG",
                ORGANIZATION,
                "Read organization information"
        ),
        UPDATE_ORG_SETTINGS(
                "P_UPDATE_ORG_SETTINGS",
                ORGANIZATION,
                "Update organization configuration and settings"
        ),
        CREATE_SPACE(
                "P_CREATE_SPACE",
                ORGANIZATION,
                "Create a new space within an organization"
        ),
        DELETE_SPACE(
                "P_DELETE_SPACE",
                ORGANIZATION,
                "Delete a space within an organization"
        ),
        MANAGE_USERS(
                "P_MANAGE_USERS",
                ORGANIZATION,
                "Manage users at organization or space level"
        ),
        MANAGE_CLIENTS(
                "P_MANAGE_CLIENTS",
                ORGANIZATION,
                "Manage OAuth2/OIDC clients at organization or space level"
        ),
        ASSIGN_ROLES(
                "P_ASSIGN_ROLES",
                ORGANIZATION,
                "Assign or revoke roles at organization or space level"
        ),
        READ_AUDIT_LOGS(
                "P_READ_AUDIT_LOGS",
                ORGANIZATION,
                "Read audit logs for organization, spaces or self"
        ),
        EXPORT_AUDIT_LOGS(
                "P_EXPORT_AUDIT_LOGS",
                ORGANIZATION,
                "Export audit logs at organization level"
        ),
        READ_POLICY(
                "P_READ_POLICY",
                ORGANIZATION,
                "Read security and authorization policies"
        ),
        UPDATE_POLICY(
                "P_UPDATE_POLICY",
                ORGANIZATION,
                "Update security and authorization policies"
        );

        private final String code;
        private final AuthorityPlan plan;
        private final String description;

        TechnicalPermission(String code, AuthorityPlan plan, String description) {
            this.code = code;
            this.plan = plan;
            this.description = description;
        }

        public String code() {
            return code;
        }

        public AuthorityPlan plan() {
            return plan;
        }

        public String description() {
            return description;
        }

        public static Optional<TechnicalPermission> fromCode(String code) {
            return Arrays.stream(values())
                    .filter(p -> p.code.equals(code))
                    .findFirst();
        }
    }
}
