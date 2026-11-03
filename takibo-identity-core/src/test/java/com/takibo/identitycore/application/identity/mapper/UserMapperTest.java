package com.takibo.identitycore.application.identity.mapper;

import com.takibo.identitycore.application.identity.readmodel.UserReadModel;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la réponse user : identité locale (id, space) exposée, ancre de compte
 * (accountId) et organisation jamais exposées.
 */
class UserMapperTest {

    private static final UUID USER_UUID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant LAST_LOGIN = Instant.parse("2026-03-01T00:00:00Z");

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void responseContract_neverCarriesTheAccountOrOrganizationAnchor() {
        List<String> components = Arrays.stream(UserResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .containsExactly("id", "spaceId", "username", "email", "firstName", "lastName",
                        "status", "type", "mfaEnabled", "passwordExpired", "lastLoginAt",
                        "createdAt", "updatedAt", "version")
                .doesNotContain("accountId", "orgId", "organizationId", "password", "passwordHash");
    }

    @Test
    void fromAggregate_unwrapsIdentifiers_andUsesTheCanonicalEmail() {
        User user = User.builder()
                .id(UserId.of(USER_UUID)).orgId(ORG_UUID).spaceId(SpaceId.of(SPACE_UUID))
                .accountId(AccountId.of(ACCOUNT_UUID))
                .username("jdoe").firstName("John").lastName("Doe")
                .status(UserStatus.LOCKED)
                .type(com.takibo.identitycore.domain.model.UserType.FEDERATED)
                .mfaEnabled(true).passwordExpired(true)
                .lastLoginAt(LAST_LOGIN).createdAt(CREATED).updatedAt(UPDATED).version(9L)
                .build();

        UserResponse response = mapper.toUserResponse(user, new EmailAddress(" John.Doe@Example.com "));

        assertThat(response.id()).isEqualTo(USER_UUID);
        assertThat(response.spaceId()).isEqualTo(SPACE_UUID);
        assertThat(response.email()).isEqualTo("john.doe@example.com");
        assertThat(response.username()).isEqualTo("jdoe");
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.status()).isEqualTo(UserStatus.LOCKED);
        assertThat(response.type()).isEqualTo(UserType.FEDERATED);
        assertThat(response.mfaEnabled()).isTrue();
        assertThat(response.passwordExpired()).isTrue();
        assertThat(response.lastLoginAt()).isEqualTo(LAST_LOGIN);
        assertThat(response.createdAt()).isEqualTo(CREATED);
        assertThat(response.updatedAt()).isEqualTo(UPDATED);
        assertThat(response.version()).isEqualTo(9L);
    }

    @ParameterizedTest
    @EnumSource(com.takibo.identitycore.domain.model.UserType.class)
    void fromAggregate_everyUserTypeIsTranslated(com.takibo.identitycore.domain.model.UserType type) {
        User user = User.builder().id(UserId.of(USER_UUID)).spaceId(SpaceId.of(SPACE_UUID)).type(type).build();

        assertThat(mapper.toUserResponse(user, null).type().name()).isEqualTo(type.name());
    }

    @Test
    void fromAggregate_withoutEmailOrIdentifiers_yieldsNullFields() {
        UserResponse response = mapper.toUserResponse(User.builder().username("jdoe").build(), null);

        assertThat(response.id()).isNull();
        assertThat(response.spaceId()).isNull();
        assertThat(response.email()).isNull();
        assertThat(response.type()).isNull();
        assertThat(response.username()).isEqualTo("jdoe");
    }

    @Test
    void fromAggregate_bothSourcesNull_yieldsNull() {
        assertThat(mapper.toUserResponse(null, null)).isNull();
    }

    @Test
    void fromReadModel_dropsTheAccountAnchor_andKeepsTheLocalView() {
        UserReadModel readModel = new UserReadModel(USER_UUID, SPACE_UUID, ACCOUNT_UUID, "a@b.io",
                "jdoe", "John", "Doe", UserStatus.ACTIVE, UserType.NATIVE, false, true,
                LAST_LOGIN, CREATED, UPDATED, 2L);

        UserResponse response = mapper.toUserResponse(readModel);

        assertThat(response).isEqualTo(new UserResponse(USER_UUID, SPACE_UUID, "jdoe", "a@b.io",
                "John", "Doe", UserStatus.ACTIVE, UserType.NATIVE, false, true,
                LAST_LOGIN, CREATED, UPDATED, 2L));
        assertThat(mapper.toUserResponse((UserReadModel) null)).isNull();
    }
}
