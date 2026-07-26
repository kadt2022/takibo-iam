package com.takibo.identitycore.domain.catalogrbac;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.ASSIGN;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.CREATE;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.DEACTIVATE;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.DELETE;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.EXPORT;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.LIFECYCLE;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.MANAGE;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.READ;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.REQUEST_DELETION;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.ROTATE_SECRET;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.SUSPEND;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.TRANSFER_OWNERSHIP;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityAction.UPDATE;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.ORGANIZATION;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.PLATFORM;
import static com.takibo.identitycore.domain.catalogrbac.AuthorityPlan.SPACE;

/**
 * Canonical RBAC permission vocabulary.
 *
 * <p>This enum deliberately does not map permissions to roles. That matrix belongs to
 * RBAC-02; RBAC-01 only defines the three-plane vocabulary.</p>
 */
public enum TechnicalPermission {

    PLATFORM_ORGS_READ(
            "P_PLATFORM_ORGS_READ", PLATFORM, AuthorityResource.ORGANIZATIONS, READ,
            "Read the platform organization catalog and administrative information"),
    PLATFORM_ORGS_CREATE(
            "P_PLATFORM_ORGS_CREATE", PLATFORM, AuthorityResource.ORGANIZATIONS, CREATE,
            "Create an organization from the platform control plane"),
    PLATFORM_ORGS_SUSPEND(
            "P_PLATFORM_ORGS_SUSPEND", PLATFORM, AuthorityResource.ORGANIZATIONS, SUSPEND,
            "Suspend or reactivate an organization from the platform control plane"),
    PLATFORM_ORGS_DELETE(
            "P_PLATFORM_ORGS_DELETE", PLATFORM, AuthorityResource.ORGANIZATIONS, DELETE,
            "Permanently delete an organization under retention and security rules"),
    PLATFORM_POLICY_READ(
            "P_PLATFORM_POLICY_READ", PLATFORM, AuthorityResource.POLICY, READ,
            "Read platform security policies"),
    PLATFORM_POLICY_UPDATE(
            "P_PLATFORM_POLICY_UPDATE", PLATFORM, AuthorityResource.POLICY, UPDATE,
            "Update platform security policies"),
    PLATFORM_AUDIT_READ(
            "P_PLATFORM_AUDIT_READ", PLATFORM, AuthorityResource.AUDIT, READ,
            "Read events emitted by the platform control plane"),
    PLATFORM_AUDIT_EXPORT(
            "P_PLATFORM_AUDIT_EXPORT", PLATFORM, AuthorityResource.AUDIT, EXPORT,
            "Export events emitted by the platform control plane"),

    ORG_READ(
            "P_ORG_READ", ORGANIZATION, AuthorityResource.ORGANIZATIONS, READ,
            "Read organization information and visible configuration"),
    ORG_UPDATE(
            "P_ORG_UPDATE", ORGANIZATION, AuthorityResource.ORGANIZATIONS, UPDATE,
            "Update organization administrative settings"),
    ORG_OWNERSHIP_TRANSFER(
            "P_ORG_OWNERSHIP_TRANSFER", ORGANIZATION, AuthorityResource.ORGANIZATIONS,
            TRANSFER_OWNERSHIP, "Transfer organization ownership atomically"),
    ORG_DEACTIVATE(
            "P_ORG_DEACTIVATE", ORGANIZATION, AuthorityResource.ORGANIZATIONS, DEACTIVATE,
            "Deactivate the organization voluntarily"),
    ORG_DELETION_REQUEST(
            "P_ORG_DELETION_REQUEST", ORGANIZATION, AuthorityResource.ORGANIZATIONS,
            REQUEST_DELETION, "Request permanent organization deletion"),
    ORG_SPACES_READ(
            "P_ORG_SPACES_READ", ORGANIZATION, AuthorityResource.SPACES, READ,
            "Read all spaces in the organization"),
    ORG_SPACES_CREATE(
            "P_ORG_SPACES_CREATE", ORGANIZATION, AuthorityResource.SPACES, CREATE,
            "Create a space in the organization"),
    ORG_SPACES_MANAGE(
            "P_ORG_SPACES_MANAGE", ORGANIZATION, AuthorityResource.SPACES, MANAGE,
            "Update, suspend or reactivate spaces in the organization"),
    ORG_SPACES_DELETE(
            "P_ORG_SPACES_DELETE", ORGANIZATION, AuthorityResource.SPACES, DELETE,
            "Delete a space under lifecycle rules"),
    ORG_USERS_READ(
            "P_ORG_USERS_READ", ORGANIZATION, AuthorityResource.USERS, READ,
            "Read users in the organization and its spaces"),
    ORG_USERS_MANAGE(
            "P_ORG_USERS_MANAGE", ORGANIZATION, AuthorityResource.USERS, MANAGE,
            "Create or update users in the organization and its spaces"),
    ORG_USERS_LIFECYCLE(
            "P_ORG_USERS_LIFECYCLE", ORGANIZATION, AuthorityResource.USERS, LIFECYCLE,
            "Suspend, reactivate, lock or deactivate organization users"),
    ORG_CLIENTS_READ(
            "P_ORG_CLIENTS_READ", ORGANIZATION, AuthorityResource.CLIENTS, READ,
            "Read OAuth2 and OIDC clients in the organization and its spaces"),
    ORG_CLIENTS_MANAGE(
            "P_ORG_CLIENTS_MANAGE", ORGANIZATION, AuthorityResource.CLIENTS, MANAGE,
            "Create or update OAuth2 and OIDC clients"),
    ORG_CLIENTS_ROTATE_SECRET(
            "P_ORG_CLIENTS_ROTATE_SECRET", ORGANIZATION, AuthorityResource.CLIENTS,
            ROTATE_SECRET, "Rotate an organization client secret"),
    ORG_CLIENTS_LIFECYCLE(
            "P_ORG_CLIENTS_LIFECYCLE", ORGANIZATION, AuthorityResource.CLIENTS, LIFECYCLE,
            "Suspend, reactivate or revoke an organization client"),
    ORG_RBAC_READ(
            "P_ORG_RBAC_READ", ORGANIZATION, AuthorityResource.RBAC, READ,
            "Read roles, groups and permissions available in the organization"),
    ORG_RBAC_ASSIGN(
            "P_ORG_RBAC_ASSIGN", ORGANIZATION, AuthorityResource.RBAC, ASSIGN,
            "Assign or revoke roles and groups within authorized boundaries"),
    ORG_POLICY_READ(
            "P_ORG_POLICY_READ", ORGANIZATION, AuthorityResource.POLICY, READ,
            "Read organization security policies"),
    ORG_POLICY_UPDATE(
            "P_ORG_POLICY_UPDATE", ORGANIZATION, AuthorityResource.POLICY, UPDATE,
            "Update organization security policies"),
    ORG_AUDIT_READ(
            "P_ORG_AUDIT_READ", ORGANIZATION, AuthorityResource.AUDIT, READ,
            "Read organization audit events and authorized space aggregation"),
    ORG_AUDIT_EXPORT(
            "P_ORG_AUDIT_EXPORT", ORGANIZATION, AuthorityResource.AUDIT, EXPORT,
            "Export organization and space audit events"),

    SPACE_READ(
            "P_SPACE_READ", SPACE, AuthorityResource.SPACES, READ,
            "Read information and visible configuration for the target space"),
    SPACE_UPDATE(
            "P_SPACE_UPDATE", SPACE, AuthorityResource.SPACES, UPDATE,
            "Update settings for the target space"),
    SPACE_USERS_READ(
            "P_SPACE_USERS_READ", SPACE, AuthorityResource.USERS, READ,
            "Read users in the target space"),
    SPACE_USERS_MANAGE(
            "P_SPACE_USERS_MANAGE", SPACE, AuthorityResource.USERS, MANAGE,
            "Create or update users in the target space"),
    SPACE_USERS_LIFECYCLE(
            "P_SPACE_USERS_LIFECYCLE", SPACE, AuthorityResource.USERS, LIFECYCLE,
            "Suspend, reactivate, lock or deactivate users in the target space"),
    SPACE_CLIENTS_READ(
            "P_SPACE_CLIENTS_READ", SPACE, AuthorityResource.CLIENTS, READ,
            "Read OAuth2 and OIDC clients in the target space"),
    SPACE_CLIENTS_MANAGE(
            "P_SPACE_CLIENTS_MANAGE", SPACE, AuthorityResource.CLIENTS, MANAGE,
            "Create or update clients in the target space"),
    SPACE_CLIENTS_ROTATE_SECRET(
            "P_SPACE_CLIENTS_ROTATE_SECRET", SPACE, AuthorityResource.CLIENTS,
            ROTATE_SECRET, "Rotate a client secret in the target space"),
    SPACE_CLIENTS_LIFECYCLE(
            "P_SPACE_CLIENTS_LIFECYCLE", SPACE, AuthorityResource.CLIENTS, LIFECYCLE,
            "Suspend, reactivate or revoke a client in the target space"),
    SPACE_RBAC_READ(
            "P_SPACE_RBAC_READ", SPACE, AuthorityResource.RBAC, READ,
            "Read roles, groups and permissions available in the target space"),
    SPACE_RBAC_ASSIGN(
            "P_SPACE_RBAC_ASSIGN", SPACE, AuthorityResource.RBAC, ASSIGN,
            "Assign or revoke authorized roles and groups in the target space"),
    SPACE_POLICY_READ(
            "P_SPACE_POLICY_READ", SPACE, AuthorityResource.POLICY, READ,
            "Read security policies for the target space"),
    SPACE_POLICY_UPDATE(
            "P_SPACE_POLICY_UPDATE", SPACE, AuthorityResource.POLICY, UPDATE,
            "Update security policies for the target space"),
    SPACE_AUDIT_READ(
            "P_SPACE_AUDIT_READ", SPACE, AuthorityResource.AUDIT, READ,
            "Read audit events for the target space"),
    SPACE_AUDIT_EXPORT(
            "P_SPACE_AUDIT_EXPORT", SPACE, AuthorityResource.AUDIT, EXPORT,
            "Export audit events for the target space");

    private static final Map<String, TechnicalPermission> BY_CODE =
            Collections.unmodifiableMap(Arrays.stream(values())
                    .collect(Collectors.toMap(TechnicalPermission::code, Function.identity())));

    private final String code;
    private final AuthorityPlan plan;
    private final AuthorityResource resource;
    private final AuthorityAction action;
    private final String description;

    TechnicalPermission(
            String code,
            AuthorityPlan plan,
            AuthorityResource resource,
            AuthorityAction action,
            String description
    ) {
        this.code = code;
        this.plan = plan;
        this.resource = resource;
        this.action = action;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public AuthorityPlan plan() {
        return plan;
    }

    public AuthorityResource resource() {
        return resource;
    }

    public AuthorityAction action() {
        return action;
    }

    public String description() {
        return description;
    }

    public static Optional<TechnicalPermission> fromCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
