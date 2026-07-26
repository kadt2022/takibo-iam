package com.takibo.identitycore.domain.catalogrbac;

/**
 * Action expressed by a canonical technical permission.
 */
public enum AuthorityAction {
    READ,
    CREATE,
    UPDATE,
    SUSPEND,
    DELETE,
    TRANSFER_OWNERSHIP,
    DEACTIVATE,
    REQUEST_DELETION,
    MANAGE,
    LIFECYCLE,
    ROTATE_SECRET,
    ASSIGN,
    EXPORT
}
