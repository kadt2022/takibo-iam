package com.takibo.securitycontext.model;

public enum AuthenticationMethod {
    PASSWORD,
    MFA,
    OAUTH2,
    OIDC,
    CERTIFICATE,
    API_KEY,
    NONE
}
