// identitycore/domain/model/UserType.java
package com.takibo.identitycore.domain.type;

public enum UserType {
    NATIVE,      // Utilisateurs internes takibo (admin, auditeur…)
    FEDERATED,   // Utilisateurs externes OAuth2/OIDC/LDAP
    MACHINE_ACCOUNT,     // Comptes techniques (client credentials)
    GUEST        // Comptes invités / temporaires
}
