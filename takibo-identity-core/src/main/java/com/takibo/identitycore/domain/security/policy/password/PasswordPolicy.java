package com.takibo.identitycore.domain.security.policy.password;

import com.takibo.identitycore.domain.exception.PasswordPolicyViolationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Getter
@Setter
@NoArgsConstructor
public class PasswordPolicy {

    // BCrypt ne hache que les 72 premiers octets et refuse au-delà : la limite fait partie de la
    // politique, pour que le refus soit une violation de politique et non une erreur du hacheur.
    static final int MAX_BYTES = 72;

    private static final Pattern UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

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
        return violation(password).isEmpty();
    }

    /**
     * Valide un mot de passe et lève une exception si invalide (mode métier : messages précis).
     * @param password mot de passe à tester
     * @throws PasswordPolicyViolationException si non conforme
     */
    public void validate(String password) {
        violation(password).ifPresent(message -> {
            throw new PasswordPolicyViolationException(message);
        });
        // Note: La vérification de l'historique et de l'âge du mot de passe irait ici, si implémentée.
    }

    /** Première règle enfreinte, dans l'ordre de déclaration ; vide si le mot de passe est conforme. */
    private Optional<String> violation(String password) {
        if (password == null) {
            return Optional.of("Password cannot be null");
        }
        return rules()
                .filter(rule -> rule.isBrokenBy(password))
                .map(Rule::message)
                .findFirst();
    }

    private Stream<Rule> rules() {
        return Stream.of(
                new Rule(password -> password.length() < minLength,
                        String.format("Password must be at least %d characters long", minLength)),
                new Rule(password -> password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES,
                        String.format("Password must not exceed %d bytes", MAX_BYTES)),
                new Rule(password -> requireUppercase && !UPPERCASE.matcher(password).matches(),
                        "Password must contain at least one uppercase letter"),
                new Rule(password -> requireLowercase && !LOWERCASE.matcher(password).matches(),
                        "Password must contain at least one lowercase letter"),
                new Rule(password -> requireDigit && !DIGIT.matcher(password).matches(),
                        "Password must contain at least one digit"),
                new Rule(password -> requireSpecialChar && !SPECIAL.matcher(password).matches(),
                        "Password must contain at least one special character"));
    }

    private record Rule(Predicate<String> broken, String message) {
        boolean isBrokenBy(String password) {
            return broken.test(password);
        }
    }
}
