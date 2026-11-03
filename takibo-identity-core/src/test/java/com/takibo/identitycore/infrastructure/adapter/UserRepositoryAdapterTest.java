package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.UserEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.UserJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    private static final UUID USER_UUID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID OTHER_UUID = UUID.fromString("99999999-0000-0000-0000-000000000009");

    @Mock
    private JpaUserRepository jpa;

    @Mock
    private UserJpaMapper mapper;

    @InjectMocks
    private UserRepositoryAdapter adapter;

    @Test
    void findBySpaceAndAccount_existing_returnsMappedUser() {
        UserEntity entity = new UserEntity();
        User user = mock(User.class);
        when(jpa.findBySpaceIdAndAccountId(SPACE_UUID, ACCOUNT_UUID)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(user);

        Optional<User> result = adapter.findBySpaceAndAccount(SpaceId.of(SPACE_UUID), AccountId.of(ACCOUNT_UUID));

        assertThat(result).containsSame(user);
        verify(mapper).toDomain(entity);
    }

    @Test
    void findBySpaceAndAccount_missing_returnsEmpty() {
        when(jpa.findBySpaceIdAndAccountId(SPACE_UUID, ACCOUNT_UUID)).thenReturn(Optional.empty());

        Optional<User> result = adapter.findBySpaceAndAccount(SpaceId.of(SPACE_UUID), AccountId.of(ACCOUNT_UUID));

        assertThat(result).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void findByEmail_queriesTheCanonicalEmailInsideTheSpace() {
        when(jpa.findBySpaceIdAndEmailIgnoreCase(SPACE_UUID, "john.doe@example.com")).thenReturn(Optional.empty());

        assertThat(adapter.findByEmail(SpaceId.of(SPACE_UUID), new EmailAddress("  John.Doe@Example.COM ")))
                .isEmpty();
        verify(jpa).findBySpaceIdAndEmailIgnoreCase(SPACE_UUID, "john.doe@example.com");
    }

    @Test
    void uniquenessChecks_areScopedToTheSpaceAndExcludeTheUserItself() {
        when(jpa.existsBySpaceIdAndUsernameIgnoreCaseAndIdNot(SPACE_UUID, "jdoe", USER_UUID)).thenReturn(true);
        when(jpa.existsBySpaceIdAndEmailIgnoreCaseAndIdNot(SPACE_UUID, "a@b.io", USER_UUID)).thenReturn(false);
        when(jpa.existsBySpaceIdAndAccountId(SPACE_UUID, ACCOUNT_UUID)).thenReturn(true);

        assertThat(adapter.existsByUsernameIgnoreCaseAndIdNot(SpaceId.of(SPACE_UUID), "jdoe", USER_UUID)).isTrue();
        assertThat(adapter.existsByEmailIgnoreCaseAndIdNot(SpaceId.of(SPACE_UUID), new EmailAddress("a@b.io"), USER_UUID))
                .isFalse();
        assertThat(adapter.existsBySpaceAndAccount(SpaceId.of(SPACE_UUID), AccountId.of(ACCOUNT_UUID))).isTrue();
    }

    @Test
    void findAllBySpace_mapsEveryEntityOfThePage() {
        Pageable pageable = PageRequest.of(0, 2);
        UserEntity entity = new UserEntity();
        User user = mock(User.class);
        when(jpa.findAllBySpaceId(SPACE_UUID, pageable)).thenReturn(new PageImpl<>(List.of(entity), pageable, 5));
        when(mapper.toDomain(entity)).thenReturn(user);

        Page<User> page = adapter.findAllBySpace(SpaceId.of(SPACE_UUID), pageable);

        assertThat(page.getContent()).containsExactly(user);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void findByUsername_andFindById_mapTheEntity() {
        UserEntity entity = new UserEntity();
        User user = mock(User.class);
        when(jpa.findBySpaceIdAndUsername(SPACE_UUID, "jdoe")).thenReturn(Optional.of(entity));
        when(jpa.findById(USER_UUID)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(user);

        assertThat(adapter.findByUsername(SpaceId.of(SPACE_UUID), "jdoe")).containsSame(user);
        assertThat(adapter.findById(UserId.of(USER_UUID))).containsSame(user);
    }

    @Test
    void delete_andDeleteById_targetTheUserIdentifier() {
        User user = User.builder().id(UserId.of(USER_UUID)).build();

        adapter.delete(user);
        adapter.deleteById(UserId.of(OTHER_UUID));

        verify(jpa).deleteById(USER_UUID);
        verify(jpa).deleteById(OTHER_UUID);
    }

    /** {@code save} avec le vrai mapper MapStruct : transposition prouvée de bout en bout. */
    @Nested
    class Save {

        private final UserJpaMapper realMapper = Mappers.getMapper(UserJpaMapper.class);

        private UserRepositoryAdapter adapterWithRealMapper() {
            return new UserRepositoryAdapter(jpa, realMapper);
        }

        private UserEntity loadedEntity() {
            return UserEntity.builder()
                    .id(USER_UUID).orgId(ORG_UUID).spaceId(SPACE_UUID).accountId(ACCOUNT_UUID)
                    .username("old").firstName("Old").lastName("Name")
                    .status(UserStatus.ACTIVE)
                    .type(com.takibo.identitycore.domain.type.UserType.NATIVE)
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .version(4L)
                    .build();
        }

        @Test
        void existingUser_mutatesTheLoadedEntity_andNeverMovesItsIdentityAnchors() {
            UserEntity loaded = loadedEntity();
            when(jpa.findById(USER_UUID)).thenReturn(Optional.of(loaded));
            when(jpa.saveAndFlush(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
            Instant lastLogin = Instant.parse("2026-05-05T10:00:00Z");
            // Le domaine prétend un autre org/space/account : la persistance doit l'ignorer.
            User changed = User.builder()
                    .id(UserId.of(USER_UUID))
                    .orgId(OTHER_UUID)
                    .spaceId(SpaceId.of(OTHER_UUID))
                    .accountId(AccountId.of(OTHER_UUID))
                    .username("new").firstName("New").lastName("Person")
                    .status(UserStatus.SUSPENDED)
                    .mfaEnabled(true).passwordExpired(true)
                    .lastLoginAt(lastLogin)
                    .metadata(Map.of("dept", "audit"))
                    .build();

            User saved = adapterWithRealMapper().save(changed);

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(jpa).saveAndFlush(captor.capture());
            UserEntity persisted = captor.getValue();
            assertThat(persisted).isSameAs(loaded);
            assertThat(persisted.getOrgId()).isEqualTo(ORG_UUID);
            assertThat(persisted.getSpaceId()).isEqualTo(SPACE_UUID);
            assertThat(persisted.getAccountId()).isEqualTo(ACCOUNT_UUID);
            assertThat(persisted.getVersion()).isEqualTo(4L);
            assertThat(persisted.getUsername()).isEqualTo("new");
            assertThat(persisted.getFirstName()).isEqualTo("New");
            assertThat(persisted.getLastName()).isEqualTo("Person");
            assertThat(persisted.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(persisted.isMfaEnabled()).isTrue();
            assertThat(persisted.isPasswordExpired()).isTrue();
            assertThat(persisted.getLastLoginAt()).isEqualTo(lastLogin);
            assertThat(persisted.getMetadata()).containsExactlyEntriesOf(Map.of("dept", "audit"));
            verify(jpa, never()).save(any());

            assertThat(saved.getSpaceId()).isEqualTo(SpaceId.of(SPACE_UUID));
            assertThat(saved.getVersion()).isEqualTo(4L);
        }

        @Test
        void newUser_isInsertedFromTheMappedEntity() {
            when(jpa.findById(USER_UUID)).thenReturn(Optional.empty());
            when(jpa.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
            User fresh = User.builder()
                    .id(UserId.of(USER_UUID))
                    .orgId(ORG_UUID)
                    .spaceId(SpaceId.of(SPACE_UUID))
                    .accountId(AccountId.of(ACCOUNT_UUID))
                    .username("jdoe")
                    .build();

            User saved = adapterWithRealMapper().save(fresh);

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(jpa).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(USER_UUID);
            assertThat(captor.getValue().getSpaceId()).isEqualTo(SPACE_UUID);
            assertThat(captor.getValue().getVersion()).isNull();
            verify(jpa, never()).saveAndFlush(any());
            assertThat(saved.getId()).isEqualTo(UserId.of(USER_UUID));
            assertThat(saved.getAccountId()).isEqualTo(AccountId.of(ACCOUNT_UUID));
        }
    }
}
