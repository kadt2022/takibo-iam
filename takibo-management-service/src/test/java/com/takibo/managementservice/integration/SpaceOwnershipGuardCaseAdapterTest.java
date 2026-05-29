package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.managementservice.infrastructure.jpa.repository.SpaceOwnershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceOwnershipGuardCaseAdapterTest {

    private static final UUID ORG_A   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ORG_B   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID SPACE_A = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock
    private SpaceOwnershipRepository repository;

    @InjectMocks
    private SpaceOwnershipGuardCaseAdapter adapter;

    @Test
    void assertSpaceBelongsToOrg_sameOrg_allowed() {
        when(repository.findOrgIdBySpaceId(SPACE_A)).thenReturn(Optional.of(ORG_A));

        assertThatNoException().isThrownBy(() -> adapter.assertSpaceBelongsToOrg(SPACE_A, ORG_A));
    }

    @Test
    void assertSpaceBelongsToOrg_differentOrg_denied() {
        when(repository.findOrgIdBySpaceId(SPACE_A)).thenReturn(Optional.of(ORG_B));

        assertThatThrownBy(() -> adapter.assertSpaceBelongsToOrg(SPACE_A, ORG_A))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_MISMATCH");
    }

    @Test
    void assertSpaceBelongsToOrg_unknownSpace_notFound() {
        when(repository.findOrgIdBySpaceId(SPACE_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.assertSpaceBelongsToOrg(SPACE_A, ORG_A))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void assertSpaceBelongsToOrg_expectedOrgIdNull_denied() {
        assertThatThrownBy(() -> adapter.assertSpaceBelongsToOrg(SPACE_A, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_CONTEXT_REQUIRED");
    }

    @Test
    void assertSpaceBelongsToOrg_twoCallsSameTtl_repositoryCalledOnce() {
        when(repository.findOrgIdBySpaceId(SPACE_A)).thenReturn(Optional.of(ORG_A));

        adapter.assertSpaceBelongsToOrg(SPACE_A, ORG_A);
        adapter.assertSpaceBelongsToOrg(SPACE_A, ORG_A);

        verify(repository, times(1)).findOrgIdBySpaceId(SPACE_A);
    }
}
