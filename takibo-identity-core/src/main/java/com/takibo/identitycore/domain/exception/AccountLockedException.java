package com.takibo.identitycore.domain.exception;

/** Le compte est temporairement verrouillé après trop d'échecs de connexion. */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account is temporarily locked");
    }
}
