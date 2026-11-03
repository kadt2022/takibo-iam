package com.takibo.identitycore.integration.space;

import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.SpaceContext;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.port.SpaceManagementCase;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceContextVerifierTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock private SpaceStatusCheckerCase spaceStatusChecker;
    @Mock private SpaceManagementCase spaceManagement;

    @InjectMocks
    private SpaceContextVerifier verifier;

    @Test
    void activeSpaceWithOrganization_returnsSituatedContext() {
        when(spaceManagement.findOrgIdBySpaceId(SpaceId.of(SPACE_ID))).thenReturn(Optional.of(ORG_ID));

        SpaceContext context = verifier.validateSpaceContext(SPACE_ID);

        assertThat(context.spaceId()).isEqualTo(SpaceId.of(SPACE_ID));
        assertThat(context.organizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void inactiveSpace_isRejected_beforeResolvingTheOrganization() {
        doThrow(new SpaceNotActiveException(SPACE_ID)).when(spaceStatusChecker).assertSpaceExistsAndActive(SPACE_ID);

        assertThatThrownBy(() -> verifier.validateSpaceContext(SPACE_ID))
                .isInstanceOf(SpaceNotActiveException.class);
        verifyNoInteractions(spaceManagement);
    }

    @Test
    void unknownSpace_isRejected_beforeResolvingTheOrganization() {
        doThrow(new SpaceNotFoundException(SPACE_ID)).when(spaceStatusChecker).assertSpaceExistsAndActive(SPACE_ID);

        assertThatThrownBy(() -> verifier.validateSpaceContext(SPACE_ID))
                .isInstanceOf(SpaceNotFoundException.class);
        verifyNoInteractions(spaceManagement);
    }

    @Test
    void spaceWithoutOrganization_isRejected() {
        when(spaceManagement.findOrgIdBySpaceId(SpaceId.of(SPACE_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.validateSpaceContext(SPACE_ID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining(SPACE_ID.toString());
    }

    @Test
    void nullSpaceId_isRejected_withoutAnyLookup() {
        assertThatThrownBy(() -> verifier.validateSpaceContext(null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(spaceStatusChecker, spaceManagement);
    }
}
