package com.takibo.audit.spi;


/**
 * SPI pour exposer l'identifiant utilisateur courant
 *
 * @return Identifiant utilisateur ou null si non disponible
 */
@FunctionalInterface
public interface TakiboAuditUserContext {
    String getUserId();
}