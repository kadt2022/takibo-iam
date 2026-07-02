package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserQueryRepositoryAdapterTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private JpaUserRepository jpa;

    @InjectMocks
    private UserQueryRepositoryAdapter adapter;

    @Test
    void nullOrBlankQ_neverBindsQParameter() {
        // PostgreSQL infère bytea pour un paramètre String null dans lower() :
        // la variante sans recherche doit être utilisée.
        adapter.findBySpace(SPACE_ID, UserStatus.ACTIVE, null, null, PAGE);
        adapter.findBySpace(SPACE_ID, UserStatus.ACTIVE, null, "   ", PAGE);

        verify(jpa, org.mockito.Mockito.times(2))
                .findReadModelsBySpace(eq(SPACE_ID), eq(UserStatus.ACTIVE), isNull(), eq(PAGE));
        verify(jpa, never()).searchReadModelsBySpace(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonBlankQ_isTrimmedLowercasedAndWrapped() {
        adapter.findBySpace(SPACE_ID, null, null, "  EmMa ", PAGE);

        verify(jpa).searchReadModelsBySpace(eq(SPACE_ID), isNull(), isNull(), eq("%emma%"), eq(PAGE));
    }
}
