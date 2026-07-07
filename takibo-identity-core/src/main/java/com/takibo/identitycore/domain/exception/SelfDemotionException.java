package com.takibo.identitycore.domain.exception;

/**
 * Un admin ne se retire pas à lui-même un pouvoir d'administration sur cette
 * surface : la rétrogradation de soi-même exige un autre admin (409).
 */
public class SelfDemotionException extends RuntimeException {
    public SelfDemotionException(String message) {
        super(message);
    }
}
