package com.takibo.identitycore.domain.status;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public enum UserStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    SUSPENDED,
    LOCKED,
    PASSWORD_RESET,
    DEACTIVATED;

    private static final Map<UserStatus, EnumSet<UserStatus>> ALLOWED = new EnumMap<>(UserStatus.class);
    static {
        // Depuis PENDING_ACTIVATION : activer ou désactiver
        ALLOWED.put(PENDING_ACTIVATION, EnumSet.of(ACTIVE, DEACTIVATED));

        // Depuis ACTIVE : suspendre, verrouiller, forcer reset pwd, désactiver
        ALLOWED.put(ACTIVE, EnumSet.of(SUSPENDED, LOCKED, PASSWORD_RESET, DEACTIVATED));

        // Depuis SUSPENDED : réactiver ou désactiver
        ALLOWED.put(SUSPENDED, EnumSet.of(ACTIVE, DEACTIVATED));

        // Depuis LOCKED : réactiver ou désactiver
        ALLOWED.put(LOCKED, EnumSet.of(ACTIVE, DEACTIVATED));

        // Depuis PASSWORD_RESET : réactiver (après changement) ou désactiver
        ALLOWED.put(PASSWORD_RESET, EnumSet.of(ACTIVE, DEACTIVATED));

        // Depuis DEACTIVATED : aucune transition
        ALLOWED.put(DEACTIVATED, EnumSet.noneOf(UserStatus.class));
    }

    /** Règle métier : est-ce que 'this' peut aller vers 'target' ? */
    public boolean canTransitionTo(UserStatus target) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(UserStatus.class)).contains(target);
    }

    /** Statuts qui exigent la révocation d'accès (sessions/tokens). */
    public boolean requiresRevocation() {
        return switch (this) {
            case DEACTIVATED, LOCKED, SUSPENDED, PASSWORD_RESET -> true;
            case ACTIVE, PENDING_ACTIVATION -> false;
        };
    }
}
