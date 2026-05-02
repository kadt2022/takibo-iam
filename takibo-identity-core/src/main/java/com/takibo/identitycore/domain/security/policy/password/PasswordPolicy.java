package com.takibo.identitycore.domain.security.policy.password;

import com.takibo.identitycore.domain.exception.PasswordPolicyViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PasswordPolicy {

    private UUID id;
    private String name;
    private int minLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigit;
    private boolean requireSpecialChar;
    private int maxAgeDays;
    private int historySize;
    private boolean defaultPolicy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;


    @SuppressWarnings("java:S107")
    public PasswordPolicy(UUID id, String name, int minLength, boolean requireUppercase,
                          boolean requireLowercase, boolean requireDigit, boolean requireSpecialChar,
                          int maxAgeDays, int historySize, boolean defaultPolicy) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Policy name cannot be null");
        this.minLength = minLength;
        this.requireUppercase = requireUppercase;
        this.requireLowercase = requireLowercase;
        this.requireDigit = requireDigit;
        this.requireSpecialChar = requireSpecialChar;
        this.maxAgeDays = maxAgeDays;
        this.historySize = historySize;
        this.defaultPolicy = defaultPolicy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (id == null || o == null || getClass() != o.getClass()) return false;
        PasswordPolicy that = (PasswordPolicy) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Valide un mot de passe par rapport à cette politique
     * @param password Le mot de passe à valider
     * @return true si le mot de passe est valide, false sinon
     */
    public boolean isValid(String password) {
        if (password == null || password.length() < minLength) {
            return false;
        }

        if (requireUppercase && !password.matches(".*[A-Z].*")) {
            return false;
        }

        if (requireLowercase && !password.matches(".*[a-z].*")) {
            return false;
        }

        if (requireDigit && !password.matches(".*\\d.*")) {
            return false;
        }

        return !requireSpecialChar || password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    }

    /**
     * Valide un mot de passe et lève une exception si invalide (mode métier : messages précis).
     * @param password mot de passe à tester
     * @throws PasswordPolicyViolationException si non conforme
     */
    public void validate(String password) {
        if (password == null) {
            throw new PasswordPolicyViolationException("Password cannot be null");
        }

        if (password.length() < minLength) {
            throw new PasswordPolicyViolationException(
                    String.format("Password must be at least %d characters long", minLength)
            );
        }
        if (requireUppercase && !password.matches(".*[A-Z].*")) {
            throw new PasswordPolicyViolationException("Password must contain at least one uppercase letter");
        }
        if (requireLowercase && !password.matches(".*[a-z].*")) {
            throw new PasswordPolicyViolationException("Password must contain at least one lowercase letter");
        }
        if (requireDigit && !password.matches(".*\\d.*")) {
            throw new PasswordPolicyViolationException("Password must contain at least one digit");
        }
        if (requireSpecialChar && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new PasswordPolicyViolationException("Password must contain at least one special character");
        }

        // Note: La vérification de l'historique et de l'âge du mot de passe irait ici, si implémentée.
    }
}
