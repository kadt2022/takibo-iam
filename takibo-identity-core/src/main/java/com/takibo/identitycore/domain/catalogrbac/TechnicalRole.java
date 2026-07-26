package com.takibo.identitycore.domain.catalogrbac;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.ORGANIZATION;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.PLATFORM;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.SPACE;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.ASSIGN_ROLES;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.CREATE_ORG;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.CREATE_SPACE;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.DELETE_ORG;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.DELETE_SPACE;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.EXPORT_AUDIT_LOGS;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.MANAGE_CLIENTS;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.MANAGE_USERS;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.READ_AUDIT_LOGS;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.READ_ORG;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.READ_POLICY;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.UPDATE_ORG_SETTINGS;
import static com.takibo.identitycore.domain.catalogrbac.TechnicalGroup.TechnicalPermission.UPDATE_POLICY;

/**
 * Canonical technical roles and their model-level characteristics.
 *
 * <p>The legacy permission sets are retained unchanged until RBAC-02 introduces the
 * canonical role-to-permission matrix. They must not be interpreted as that matrix.</p>
 */
public enum TechnicalRole {

    PLATFORM_ADMIN(
            "R_TAKIBO_PLATFORM_ADMIN",
            "Platform administrator",
            "Administers the TAKIBO platform control plane",
            PLATFORM,
            new RoleCharacteristics(false, false, true),
            false,
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

    PLATFORM_AUDITOR(
            "R_TAKIBO_PLATFORM_AUDITOR",
            "Platform auditor",
            "Reads events, policies and operations of the platform control plane",
            PLATFORM,
            new RoleCharacteristics(false, false, false),
            false,
            Set.of(
                    READ_ORG,
                    READ_AUDIT_LOGS,
                    READ_POLICY
            )
    ),

    ORG_OWNER(
            "R_ORG_OWNER",
            "Organization owner",
            "Owns the organization and its non-delegable lifecycle powers",
            ORGANIZATION,
            new RoleCharacteristics(false, false, true),
            false,
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
            "Organization administrator",
            "Administers the organization and all of its spaces",
            ORGANIZATION,
            new RoleCharacteristics(true, true, true),
            false,
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
            "Organization user administrator",
            "Administers human identities in the organization and its spaces",
            ORGANIZATION,
            new RoleCharacteristics(true, true, true),
            false,
            Set.of(MANAGE_USERS)
    ),

    ORG_CLIENT_ADMIN(
            "R_ORG_CLIENT_ADMIN",
            "Organization client administrator",
            "Administers OAuth2 and OIDC clients in the organization and its spaces",
            ORGANIZATION,
            new RoleCharacteristics(true, true, true),
            false,
            Set.of(MANAGE_CLIENTS)
    ),

    ORG_AUDITOR(
            "R_ORG_AUDITOR",
            "Organization auditor",
            "Reads and exports audit events for the organization and its spaces",
            ORGANIZATION,
            new RoleCharacteristics(true, true, false),
            false,
            Set.of(
                    READ_ORG,
                    READ_AUDIT_LOGS,
                    EXPORT_AUDIT_LOGS,
                    READ_POLICY
            )
    ),

    SPACE_ADMIN(
            "R_SPACE_ADMIN",
            "Space administrator",
            "Administers all resources of one specific space",
            SPACE,
            new RoleCharacteristics(true, true, true),
            false,
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
            "Space user administrator",
            "Administers users in one specific space",
            SPACE,
            new RoleCharacteristics(true, true, true),
            false,
            Set.of(MANAGE_USERS)
    ),

    SPACE_CLIENT_ADMIN(
            "R_SPACE_CLIENT_ADMIN",
            "Space client administrator",
            "Administers OAuth2 and OIDC clients in one specific space",
            SPACE,
            new RoleCharacteristics(true, true, true),
            false,
            Set.of(MANAGE_CLIENTS)
    ),

    SPACE_AUDITOR(
            "R_SPACE_AUDITOR",
            "Space auditor",
            "Reads and exports audit events for one specific space",
            SPACE,
            new RoleCharacteristics(true, true, false),
            false,
            Set.of()
    ),

    @Deprecated(since = "RBAC-01")
    ORG_VIEWER(
            "R_ORG_VIEWER",
            "Organization viewer",
            "Deprecated organization read-only role retained for compatibility",
            ORGANIZATION,
            new RoleCharacteristics(true, true, false),
            true,
            Set.of(
                    READ_ORG,
                    READ_POLICY
            )
    ),

    @Deprecated(since = "RBAC-01")
    SPACE_VIEWER(
            "R_SPACE_VIEWER",
            "Space viewer",
            "Deprecated space read-only role retained for compatibility",
            SPACE,
            new RoleCharacteristics(true, true, false),
            true,
            Set.of(
                    READ_AUDIT_LOGS,
                    READ_POLICY
            )
    ),

    @Deprecated(since = "RBAC-01")
    SELF(
            "R_SELF",
            "Self-service marker",
            "Deprecated self-service role retained for compatibility",
            SPACE,
            new RoleCharacteristics(false, false, false),
            true,
            Set.of(READ_AUDIT_LOGS)
    );

    private static final Map<String, TechnicalRole> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TechnicalRole::code, Function.identity()));

    private static final List<TechnicalRole> CANONICAL_VALUES = Arrays.stream(values())
            .filter(role -> !role.deprecated)
            .toList();

    private final String code;
    private final String displayName;
    private final String description;
    private final AuthorityPlan plan;
    private final RoleCharacteristics characteristics;
    private final boolean deprecated;
    private final Set<TechnicalGroup.TechnicalPermission> legacyPermissions;

    TechnicalRole(
            String code,
            String displayName,
            String description,
            AuthorityPlan plan,
            RoleCharacteristics characteristics,
            boolean deprecated,
            Set<TechnicalGroup.TechnicalPermission> legacyPermissions
    ) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.plan = plan;
        this.characteristics = characteristics;
        this.deprecated = deprecated;
        this.legacyPermissions = Set.copyOf(legacyPermissions);
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public AuthorityPlan plan() {
        return plan;
    }

    public boolean assignable() {
        return characteristics.assignable();
    }

    public boolean inheritable() {
        return characteristics.inheritable();
    }

    public boolean administrator() {
        return characteristics.administrator();
    }

    public boolean deprecated() {
        return deprecated;
    }

    public boolean selfService() {
        return "R_SELF".equals(code);
    }

    /**
     * Compatibility mapping retained until RBAC-02.
     */
    public Set<TechnicalGroup.TechnicalPermission> permissions() {
        return legacyPermissions;
    }

    public static List<TechnicalRole> canonicalValues() {
        return CANONICAL_VALUES;
    }

    public static Optional<TechnicalRole> fromCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    private record RoleCharacteristics(
            boolean assignable,
            boolean inheritable,
            boolean administrator
    ) {}
}
