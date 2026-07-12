package com.takibo.securitycontext.model;

public final class StandardAttributeKeys {

    private StandardAttributeKeys() {
    }

    public static final AttributeKey PERMISSIONS =
            new AttributeKey(StandardNamespaces.ENTITLEMENTS, "permissions");

    public static final AttributeKey ROLES =
            new AttributeKey(StandardNamespaces.ENTITLEMENTS, "roles");

    public static final AttributeKey ACCOUNT_ID =
            new AttributeKey(StandardNamespaces.IDENTITY, "accountId");

    public static final AttributeKey USER_ID =
            new AttributeKey(StandardNamespaces.IDENTITY, "userId");

    public static final AttributeKey SCOPE_LEVEL =
            new AttributeKey(StandardNamespaces.TENANT, "scopeLevel");
}
