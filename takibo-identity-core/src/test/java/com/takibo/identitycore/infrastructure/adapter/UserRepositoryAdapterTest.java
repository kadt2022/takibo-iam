package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.UserEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.UserJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

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
}
