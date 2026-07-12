package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.identitycore.application.spacecontext.port.in.CurrentUserSpaceQueryCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.interfaces.rest.response.CurrentUserSpaceItemResponse;
import com.takibo.identitycore.interfaces.rest.response.CurrentUserSpacesResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserSpaceControllerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private CurrentUserSpaceQueryCase currentUserSpaceQueryCase;

    @InjectMocks private CurrentUserSpaceController controller;

    @Test
    void listCurrentUserSpaces_returnsStableResponseWithoutInputIdentifiers() {
        CurrentUserSpacesResponse expected = new CurrentUserSpacesResponse(ORG_ID, List.of(
                new CurrentUserSpaceItemResponse(
                        SPACE_ID, "finance", "Finance", USER_ID, "ACTIVE", UserStatus.ACTIVE, true)));
        when(currentUserSpaceQueryCase.listAccessibleSpaces()).thenReturn(expected);

        ResponseEntity<CurrentUserSpacesResponse> response = controller.listCurrentUserSpaces();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(expected);
        verify(currentUserSpaceQueryCase).listAccessibleSpaces();
    }

    @Test
    void listCurrentUserSpaces_serializesEmptyList() {
        CurrentUserSpacesResponse expected = new CurrentUserSpacesResponse(ORG_ID, List.of());
        when(currentUserSpaceQueryCase.listAccessibleSpaces()).thenReturn(expected);

        ResponseEntity<CurrentUserSpacesResponse> response = controller.listCurrentUserSpaces();

        assertThat(response.getBody().items()).isEmpty();
    }
}
