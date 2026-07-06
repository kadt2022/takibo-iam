package com.takibo.identitycore.integration.security.port;

import java.util.UUID;

/**
 * Account humain porté par le token courant. Un token machine (PLATFORM,
 * client_credentials) ne porte pas d'account : les surfaces qui exigent un acteur
 * humain situé lui sont fermées par construction.
 */
public interface CurrentAccountContextCase {

    /**
     * @throws org.springframework.security.access.AccessDeniedException if no account context is available
     */
    UUID requireCurrentAccountId();
}
