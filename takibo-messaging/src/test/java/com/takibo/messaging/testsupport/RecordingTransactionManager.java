package com.takibo.messaging.testsupport;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Gestionnaire de transactions minimal qui compte les debuts, validations et annulations,
 * pour verifier que les ecritures sont bien encadrees par une transaction.
 */
public final class RecordingTransactionManager implements PlatformTransactionManager {

    public int begun;
    public int committed;
    public int rolledBack;
    /** Vrai uniquement entre getTransaction et commit/rollback. */
    public boolean active;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        begun++;
        active = true;
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        committed++;
        active = false;
    }

    @Override
    public void rollback(TransactionStatus status) {
        rolledBack++;
        active = false;
    }
}
