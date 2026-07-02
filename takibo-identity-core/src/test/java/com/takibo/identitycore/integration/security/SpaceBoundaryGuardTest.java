package com.takibo.identitycore.integration.security;

import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.integration.security.port.CurrentSpaceContextCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceBoundaryGuardTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OTHER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock
    private CurrentOrganizationContextCase currentOrganizationContext;

    @Mock
    private CurrentSpaceContextCase currentSpaceContext;

    @InjectMocks
    private SpaceBoundaryGuard guard;

    @Test
    void assertTokenMatches_matchingOrgAndSpace_allows() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(SPACE_ID);

        assertThatCode(() -> guard.assertTokenMatches(key(ORG_ID, SPACE_ID))).doesNotThrowAnyException();
    }

    @Test
    void assertTokenMatches_orgMismatch_deniesBeforeReadingSpace() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(OTHER_ID);

        assertThatThrownBy(() -> guard.assertTokenMatches(key(ORG_ID, SPACE_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_MISMATCH");
        verify(currentSpaceContext, never()).requireCurrentSpaceId();
    }

    @Test
    void assertTokenMatches_spaceMismatch_denies() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(OTHER_ID);

        assertThatThrownBy(() -> guard.assertTokenMatches(key(ORG_ID, SPACE_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_MISMATCH");
    }

    private ResolvedSpaceKey key(UUID orgId, UUID spaceId) {
        return new ResolvedSpaceKey(orgId, spaceId, "takibo-iam", "finance");
    }
}
