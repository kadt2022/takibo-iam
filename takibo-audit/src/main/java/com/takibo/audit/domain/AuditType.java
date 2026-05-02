package com.takibo.audit.domain;

public enum AuditType {
    CREATE,
    READ,
    UPDATE,
    DELETE,

    ACTION,        //  Opérations métier non-CRUD (ex: validate credentials)
    LOGIN,         // Authentification réussie
    LOGIN_FAILED,  // Échec d’authentification
    SECURITY,      // Action de sécurité (blocage, détection, mfa...)
    ACCESS,        // Accès à une ressource protégée (ex: token introspect)
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}