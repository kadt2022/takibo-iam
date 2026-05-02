package com.takibo.identitycore.infrastructure.entity;

public enum MfaFactorType {
    TOTP,
    WEBAUTHN,
    SMS,
    EMAIL,
    BACKUP_CODE
}
