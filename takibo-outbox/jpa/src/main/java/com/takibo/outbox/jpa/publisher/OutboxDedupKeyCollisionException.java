package com.takibo.outbox.jpa.publisher;

/**
 * Une clé de déduplication déjà enregistrée désigne un autre événement (type, agrégat, tenant ou
 * contenu différent). Ce n'est pas un doublon : l'appelant réutilise une clé à tort, et sa
 * transaction doit échouer plutôt que de perdre silencieusement cet événement.
 */
public class OutboxDedupKeyCollisionException extends IllegalStateException {

    public OutboxDedupKeyCollisionException(String dedupKey, String eventType) {
        super("Outbox dedup key already used by a different event | dedupKey=" + dedupKey
                + " eventType=" + eventType);
    }
}
