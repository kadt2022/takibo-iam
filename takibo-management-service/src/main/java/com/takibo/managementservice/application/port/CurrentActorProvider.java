package com.takibo.managementservice.application.port;



import com.takibo.managementservice.application.security.ActorSource;

import java.util.UUID;

public interface CurrentActorProvider {
    UUID currentUserId();

    /**
     * Account de l'acteur courant (sub / account_id du token humain).
     * IAM 31 : c'est l'identifiant de propriété — un space appartient à un
     * account, jamais à un user local (réalité de space). Présent aussi bien
     * dans un token SPACE que dans un token ORGANIZATION.
     */
    UUID currentAccountId();

    ActorSource source();
}
