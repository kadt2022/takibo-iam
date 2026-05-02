package com.takibo.audit.infrastructure.service;

/**
 * Point d'entrée "propre" pour générer une représentation masquée d'un objet
 * à destination des logs (y compris toString() des DTO REST).
 */
public final class MaskingLogger {

    private MaskingLogger() {
    }

    public static String safeToString(Object value) {
        if (value == null) {
            return "null";
        }

        MaskingService maskingService = MaskingSupport.getMaskingService();

        // Si le moteur de masquage n'est pas encore dispo (phase de bootstrap, tests unitaires...)
        if (maskingService == null) {
            return value.toString();
        }

        // MaskingService sait appliquer @Sensitive, @Mask, @AuditIgnore
        // et renvoyer un objet déjà "prêt à logger" (Map, DTO masqué, etc.)
        Object masked = maskingService.maskForLogging(value);

        return String.valueOf(masked);
    }
}
