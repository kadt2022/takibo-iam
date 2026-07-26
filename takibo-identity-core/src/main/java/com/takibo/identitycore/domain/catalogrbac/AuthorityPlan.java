package com.takibo.identitycore.domain.catalogrbac;

/**
 * Administrative plane on which a technical role or permission operates.
 */
public enum AuthorityPlan {
    PLATFORM("P_PLATFORM_"),
    ORGANIZATION("P_ORG_"),
    SPACE("P_SPACE_");

    private final String permissionCodePrefix;

    AuthorityPlan(String permissionCodePrefix) {
        this.permissionCodePrefix = permissionCodePrefix;
    }

    public String permissionCodePrefix() {
        return permissionCodePrefix;
    }
}
