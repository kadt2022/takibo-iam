package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.application.identity.readmodel.UserReadModel;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryRepositoryAdapterTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock
    private JpaUserRepository jpa;

    @InjectMocks
    private UserQueryRepositoryAdapter adapter;

    @Test
    void findBySpace_blankQuery_usesNonSearchQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserReadModel> expected = new PageImpl<>(List.of());
        when(jpa.findReadModelsBySpace(SPACE_ID, UserStatus.ACTIVE, UserType.NATIVE, pageable)).thenReturn(expected);

        Page<UserReadModel> result = adapter.findBySpace(SPACE_ID, UserStatus.ACTIVE, UserType.NATIVE, " ", pageable);

        assertThat(result).isSameAs(expected);
        verify(jpa).findReadModelsBySpace(SPACE_ID, UserStatus.ACTIVE, UserType.NATIVE, pageable);
        verify(jpa, never()).searchReadModelsBySpace(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void findBySpace_query_trimsLowercasesAndWrapsPattern() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserReadModel> expected = new PageImpl<>(List.of());
        when(jpa.searchReadModelsBySpace(SPACE_ID, null, null, "%alice%", pageable)).thenReturn(expected);

        Page<UserReadModel> result = adapter.findBySpace(SPACE_ID, null, null, "  ALICE  ", pageable);

        assertThat(result).isSameAs(expected);
        verify(jpa).searchReadModelsBySpace(SPACE_ID, null, null, "%alice%", pageable);
        verify(jpa, never()).findReadModelsBySpace(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void findBySpaceAndId_delegatesToJpa() {
        UserReadModel model = org.mockito.Mockito.mock(UserReadModel.class);
        when(jpa.findReadModelBySpaceAndId(SPACE_ID, USER_ID)).thenReturn(Optional.of(model));

        Optional<UserReadModel> result = adapter.findBySpaceAndId(SPACE_ID, USER_ID);

        assertThat(result).containsSame(model);
        verify(jpa).findReadModelBySpaceAndId(SPACE_ID, USER_ID);
    }
}
