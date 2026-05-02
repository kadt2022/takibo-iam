//package com.takibu.identitycore.domain.model;
//
//import lombok.EqualsAndHashCode;
//import lombok.Getter;
//import lombok.Builder;
//import lombok.ToString;
//
//import java.time.Instant;
//import java.util.Map;
//import java.util.Objects;
//
//
//
//@Getter
//@EqualsAndHashCode(onlyExplicitlyIncluded = true)
//@Builder(toBuilder = true)
//public class User {
//
//    @EqualsAndHashCode.Include
//    private final UserId id;
//    private final SpaceId spaceId;
//    @ToString.Include
//    private String username;
//    private EmailAddress email;
//    private Password password;
//    private String firstName;
//    private String lastName;
//    @Builder.Default
//    private UserStatus status = UserStatus.PENDING_ACTIVATION;
//    @Builder.Default
//    private boolean passwordExpired = false;
//    @Builder.Default
//    private boolean mfaEnabled = false;
//    private Instant lastLoginAt;
//    private Instant createdAt;
//    private Instant updatedAt;
//    private Map<String, Object> metadata;
//    @Builder.Default
//    private Long version = 0L;
//
//    /**
//     * Crée un nouvel utilisateur.
//     * C'est une méthode factory qui garantit un état initial valide pour un nouvel utilisateur.
//     * Elle est utilisée par le domaine pour créer de nouvelles instances d'entités.
//     * <p>
//     * Note: Les annotations de suppression de warnings sont justifiées.
//     * Le nombre de paramètres est élevé pour une clarté de la méthode factory,
//     * et le warning 'unused' est ignoré car la méthode est appelée depuis l'infrastructure.
//     */
//    @SuppressWarnings({ "java:S107", "unused" })
//    public static User createNewUser(SpaceId spaceId,
//                                     String username,
//                                     String emailValue,
//                                     String rawPassword,
//                                     String firstName,
//                                     String lastName,
//                                     PasswordHasher passwordHasherhasher,
//                                     Instant now,
//                                     Map<String, Object> metadata) {
//        Objects.requireNonNull(spaceId, "spaceId");
//        Objects.requireNonNull(username, "username");
//        Objects.requireNonNull(emailValue, "emailValue");
//        Objects.requireNonNull(rawPassword, "rawPassword");
//        Objects.requireNonNull(passwordHasherhasher, "hasher");
//        Objects.requireNonNull(now, "now");
//
//        return User.builder()
//                .id(UserId.generate())
//                .spaceId(spaceId)
//                .username(username)
//                .email(new EmailAddress(emailValue))
//                .password(new Password(passwordHasherhasher.hash(rawPassword)))
//                .firstName(firstName)
//                .lastName(lastName)
//                .status(UserStatus.PENDING_ACTIVATION)
//                .passwordExpired(true)
//                .mfaEnabled(false)
//                .createdAt(now)
//                .updatedAt(now)
//                .version(0L)
//                .metadata(metadata)
//                .build();
//    }
//}


package com.takibo.identitycore.domain.model;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import lombok.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;


@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public class User {

    @EqualsAndHashCode.Include
    private final UserId id;
    private final SpaceId spaceId;
    private final AccountId accountId;
    private String username;
    private String firstName;
    private String lastName;
    private Account account;
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
    private UserType type;
    @Builder.Default
    private boolean passwordExpired = false;
    @Builder.Default
    private boolean mfaEnabled = false;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> metadata;
    @Builder.Default
    private Long version = 0L;


    @SuppressWarnings("java:S107")
    public static User createNative(UserId id,
                                    SpaceId spaceId,
                                    AccountId accountId,
                                    String username,
                                    String firstName,
                                    String lastName,
                                    UserStatus status,
                                    boolean mfaEnabled,
                                    boolean passwordExpired,
                                    Instant lastLoginAt,
                                    Instant createdAt,
                                    Instant updatedAt,
                                    Map<String, Object> metadata) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return User.builder()
                .id(id)
                .spaceId(spaceId)
                .accountId(accountId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .status(status == null ? UserStatus.ACTIVE : status)
                .type(UserType.NATIVE)
                .mfaEnabled(mfaEnabled)
                .passwordExpired(passwordExpired)
                .lastLoginAt(lastLoginAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .metadata(metadata)
                .version(0L)
                .build();
    }

    public static User createFederated(
            SpaceId spaceId, AccountId accountId,
            String username, String firstName, String lastName,
            Map<String,Object> metadata
    ) {
        Instant now = Instant.now();
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(username, "username");
        return User.builder()
                .id(UserId.generate())
                .spaceId(spaceId)
                .accountId(accountId)
                .type(UserType.FEDERATED)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .status(UserStatus.ACTIVE)
                .passwordExpired(false)
                .mfaEnabled(false)
                .createdAt(now)
                .updatedAt(now)
                .metadata(metadata)
                .build();
    }
    public static User createService(
            SpaceId spaceId, AccountId accountId, String username, Map<String,Object> metadata
    ) {
        Instant now = Instant.now();
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(username, "username");
        return User.builder()
                .id(UserId.generate())
                .spaceId(spaceId)
                .accountId(accountId)
                .type(UserType.MACHINE_ACCOUNT)
                .username(username)
                .firstName(null)
                .lastName(null)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .metadata(metadata)
                .build();
    }

    public static User createGuest(
            SpaceId spaceId, AccountId accountId,
            String username, String firstName, String lastName,
            Map<String,Object> metadata
    ) {
        Instant now = Instant.now();
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(username, "username");
        return User.builder()
                .id(UserId.generate())
                .spaceId(spaceId)
                .accountId(accountId)
                .type(UserType.GUEST)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .metadata(metadata)
                .build();
    }
}