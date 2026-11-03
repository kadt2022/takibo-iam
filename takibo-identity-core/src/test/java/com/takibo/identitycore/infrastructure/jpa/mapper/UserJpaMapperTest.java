package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.model.UserType;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transposition domaine ↔ JPA d'un user situé : les ancres d'identité (id, org,
 * space, account) traversent la frontière sans altération, et la version JPA n'est
 * jamais fabriquée depuis le domaine.
 */
class UserJpaMapperTest {

    private static final UUID USER_UUID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant LAST_LOGIN = Instant.parse("2026-03-01T00:00:00Z");

    private final UserJpaMapper mapper = Mappers.getMapper(UserJpaMapper.class);

    private static User domainUser(UserType type, Map<String, Object> metadata) {
        return User.builder()
                .id(UserId.of(USER_UUID))
                .orgId(ORG_UUID)
                .spaceId(SpaceId.of(SPACE_UUID))
                .accountId(AccountId.of(ACCOUNT_UUID))
                .username("jdoe")
                .firstName("John")
                .lastName("Doe")
                .status(UserStatus.SUSPENDED)
                .type(type)
                .mfaEnabled(true)
                .passwordExpired(true)
                .lastLoginAt(LAST_LOGIN)
                .createdAt(CREATED)
                .updatedAt(UPDATED)
                .metadata(metadata)
                .version(7L)
                .build();
    }

    @Test
    void toEntity_transposesIdentityAnchorsAndProfile() {
        UserEntity entity = mapper.toEntity(domainUser(UserType.NATIVE, Map.of("dept", "finance")));

        assertThat(entity.getId()).isEqualTo(USER_UUID);
        assertThat(entity.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_UUID);
        assertThat(entity.getAccountId()).isEqualTo(ACCOUNT_UUID);
        assertThat(entity.getUsername()).isEqualTo("jdoe");
        assertThat(entity.getFirstName()).isEqualTo("John");
        assertThat(entity.getLastName()).isEqualTo("Doe");
        assertThat(entity.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(entity.getType()).isEqualTo(com.takibo.identitycore.domain.type.UserType.NATIVE);
        assertThat(entity.isMfaEnabled()).isTrue();
        assertThat(entity.isPasswordExpired()).isTrue();
        assertThat(entity.getLastLoginAt()).isEqualTo(LAST_LOGIN);
        assertThat(entity.getMetadata()).containsExactlyEntriesOf(Map.of("dept", "finance"));
    }

    @Test
    void toEntity_neverCarriesVersionOrTimestamps_soPersistenceOwnsThem() {
        UserEntity entity = mapper.toEntity(domainUser(UserType.NATIVE, null));

        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
        assertThat(entity.getIdentity()).isNull();
        assertThat(entity.getAccount()).isNull();
        assertThat(entity.getMetadata()).isNull();
    }

    @Test
    void toEntity_copiesMetadataDefensively() {
        Map<String, Object> metadata = new HashMap<>(Map.of("dept", "finance"));

        UserEntity entity = mapper.toEntity(domainUser(UserType.NATIVE, metadata));
        metadata.put("role", "injected");

        assertThat(entity.getMetadata()).containsOnlyKeys("dept");
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void userType_survivesTheRoundTrip(UserType type) {
        UserEntity entity = mapper.toEntity(domainUser(type, null));

        assertThat(entity.getType().name()).isEqualTo(type.name());
        assertThat(mapper.toDomain(entity).getType()).isEqualTo(type);
    }

    @Test
    void toDomain_wrapsIdentifiersAndKeepsPersistenceMetadata() {
        UserEntity entity = UserEntity.builder()
                .id(USER_UUID).orgId(ORG_UUID).spaceId(SPACE_UUID).accountId(ACCOUNT_UUID)
                .username("jdoe").firstName("John").lastName("Doe")
                .status(UserStatus.DEACTIVATED)
                .type(com.takibo.identitycore.domain.type.UserType.FEDERATED)
                .mfaEnabled(true).passwordExpired(false)
                .lastLoginAt(LAST_LOGIN).createdAt(CREATED).updatedAt(UPDATED)
                .metadata(Map.of("k", "v"))
                .version(3L)
                .build();

        User user = mapper.toDomain(entity);

        assertThat(user.getId()).isEqualTo(UserId.of(USER_UUID));
        assertThat(user.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(user.getSpaceId()).isEqualTo(SpaceId.of(SPACE_UUID));
        assertThat(user.getAccountId()).isEqualTo(AccountId.of(ACCOUNT_UUID));
        assertThat(user.getUsername()).isEqualTo("jdoe");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(user.getType()).isEqualTo(UserType.FEDERATED);
        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(user.isPasswordExpired()).isFalse();
        assertThat(user.getCreatedAt()).isEqualTo(CREATED);
        assertThat(user.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(user.getVersion()).isEqualTo(3L);
        assertThat(user.getMetadata()).containsExactlyEntriesOf(Map.of("k", "v"));
        assertThat(user.getAccount()).isNull();
    }

    @Test
    void nullInputs_mapToNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    void entityWithoutIdentifiers_yieldsNullValueObjects_insteadOfFailing() {
        User user = mapper.toDomain(UserEntity.builder().username("orphan").build());

        assertThat(user.getId()).isNull();
        assertThat(user.getSpaceId()).isNull();
        assertThat(user.getAccountId()).isNull();
        assertThat(user.getType()).isNull();
    }
}
