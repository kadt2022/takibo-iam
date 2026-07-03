package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.application.identity.port.UserQueryRepository;
import com.takibo.identitycore.application.identity.readmodel.UserReadModel;
import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserPageResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final ResolvedSpaceKey KEY =
            new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock private SpaceContextVerifier spaceContextVerifier;
    @Mock private SpaceBoundaryGuard spaceBoundaryGuard;
    @Mock private UserQueryRepository userQueryRepository;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private UserQueryService service;

    private UserReadModel readModel() {
        return new UserReadModel(USER_ID, SPACE_ID, UUID.randomUUID(), "john@example.com",
                "jdoe", "John", "Doe", UserStatus.ACTIVE, UserType.NATIVE,
                false, false, null, Instant.now(), Instant.now(), 0L);
    }

    @Test
    void listUsers_inResolvedSpace_returnsPage() {
        UserReadModel model = readModel();
        when(userQueryRepository.findBySpace(eq(SPACE_ID), eq(UserStatus.ACTIVE), isNull(), eq("jo"), any()))
                .thenReturn(new PageImpl<>(List.of(model), PageRequest.of(0, 20), 1));
        UserResponse response = mock(UserResponse.class);
        when(userMapper.toUserResponse(model)).thenReturn(response);

        UserPageResponse page = service.listUsers(KEY, UserStatus.ACTIVE, null, "jo", 0, 20);

        assertThat(page.content()).containsExactly(response);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);

        verify(spaceContextVerifier).validateSpaceContext(SPACE_ID);
        verify(spaceBoundaryGuard).assertTokenMatches(KEY);
    }

    @Test
    void getUser_inSameSpace_returnsResponse() {
        UserReadModel model = readModel();
        when(userQueryRepository.findBySpaceAndId(SPACE_ID, USER_ID)).thenReturn(Optional.of(model));
        UserResponse response = mock(UserResponse.class);
        when(userMapper.toUserResponse(model)).thenReturn(response);

        assertThat(service.getUser(KEY, USER_ID)).isSameAs(response);
    }

    @Test
    void getUser_absentFromSpace_isNotFound_antiEnumeration() {
        // Le repository est scoppé par space : un user d'un autre space est simplement absent.
        when(userQueryRepository.findBySpaceAndId(SPACE_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(KEY, USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void spaceInactive_denied_beforeAnyQuery() {
        doThrow(new SpaceNotActiveException(SPACE_ID))
                .when(spaceContextVerifier).validateSpaceContext(SPACE_ID);

        assertThatThrownBy(() -> service.listUsers(KEY, null, null, null, 0, 20))
                .isInstanceOf(SpaceNotActiveException.class);

        verifyNoInteractions(userQueryRepository);
    }

    @Test
    void tokenOutsideBoundary_denied_beforeAnyQuery() {
        doThrow(new AccessDeniedException("SPACE_CONTEXT_MISMATCH"))
                .when(spaceBoundaryGuard).assertTokenMatches(KEY);

        assertThatThrownBy(() -> service.getUser(KEY, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_MISMATCH");

        verifyNoInteractions(userQueryRepository);
    }

    @Test
    void pageSize_isClamped() {
        when(userQueryRepository.findBySpace(eq(SPACE_ID), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(KEY, null, null, null, -5, 5000);

        verify(userQueryRepository).findBySpace(eq(SPACE_ID), isNull(), isNull(), isNull(),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 100));
    }
}
