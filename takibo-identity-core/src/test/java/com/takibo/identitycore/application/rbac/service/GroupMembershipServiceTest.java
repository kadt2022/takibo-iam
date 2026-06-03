package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.rbac.model.GroupReference;
import com.takibo.identitycore.domain.rbac.model.UserGroupMembership;
import com.takibo.identitycore.domain.rbac.repository.UserGroupMembershipRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupMembershipServiceTest {

    private static final UUID ORG_UUID   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_UUID  = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID GROUP_A_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID GROUP_B_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final SpaceId SPACE_ID = SpaceId.of(SPACE_UUID);
    private static final UserId USER_ID = UserId.of(USER_UUID);

    @Mock private GroupRepository groupRepository;
    @Mock private UserGroupMembershipRepository userGroupMembershipRepository;
    @Mock private SpaceStatusCheckerCase spaceStatusCheckerCase;

    private GroupMembershipService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new GroupMembershipService(
                groupRepository,
                userGroupMembershipRepository,
                spaceStatusCheckerCase,
                clock
        );
    }

    @Test
    void addToGroups_nullOrEmptyGroupCodes_returnsWithoutSaving() {
        service.addToGroups(ORG_UUID, SPACE_ID, USER_ID, null);

        verify(spaceStatusCheckerCase).assertSpaceExistsAndActive(SPACE_UUID);
        verify(groupRepository, never()).findReferencesBySpaceIdAndCodeIn(any(), any());
        verify(userGroupMembershipRepository, never()).saveAllIdempotently(any());
    }

    @Test
    void addToGroups_unknownGroupCode_throws() {
        List<String> groupCodes = List.of("G_A", "G_B");
        when(groupRepository.findReferencesBySpaceIdAndCodeIn(SPACE_UUID, groupCodes))
                .thenReturn(List.of(new GroupReference(GROUP_A_ID, "G_A")));

        assertThatThrownBy(() -> service.addToGroups(ORG_UUID, SPACE_ID, USER_ID, groupCodes))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Unknown business group codes")
                .hasMessageContaining("G_B");

        verify(userGroupMembershipRepository, never()).saveAllIdempotently(any());
    }

    @Test
    void addToGroups_alreadyAssigned_doesNotSave() {
        List<String> groupCodes = List.of("G_A");
        when(groupRepository.findReferencesBySpaceIdAndCodeIn(SPACE_UUID, groupCodes))
                .thenReturn(List.of(new GroupReference(GROUP_A_ID, "G_A")));
        when(userGroupMembershipRepository.findExistingGroupIds(ORG_UUID, SPACE_UUID, USER_UUID, Set.of(GROUP_A_ID)))
                .thenReturn(Set.of(GROUP_A_ID));

        service.addToGroups(ORG_UUID, SPACE_ID, USER_ID, groupCodes);

        verify(userGroupMembershipRepository, never()).saveAllIdempotently(any());
    }

    @Test
    void addToGroups_partiallyNew_savesOnlyMissingMemberships() {
        List<String> groupCodes = List.of("G_A", "G_B");
        when(groupRepository.findReferencesBySpaceIdAndCodeIn(SPACE_UUID, groupCodes))
                .thenReturn(List.of(
                        new GroupReference(GROUP_A_ID, "G_A"),
                        new GroupReference(GROUP_B_ID, "G_B")
                ));
        when(userGroupMembershipRepository.findExistingGroupIds(
                ORG_UUID, SPACE_UUID, USER_UUID, Set.of(GROUP_A_ID, GROUP_B_ID)))
                .thenReturn(Set.of(GROUP_A_ID));

        service.addToGroups(ORG_UUID, SPACE_ID, USER_ID, groupCodes);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserGroupMembership>> captor = ArgumentCaptor.forClass(List.class);
        verify(userGroupMembershipRepository).saveAllIdempotently(captor.capture());

        assertThat(captor.getValue()).containsExactly(
                new UserGroupMembership(ORG_UUID, SPACE_UUID, USER_UUID, GROUP_B_ID, NOW, null)
        );
        verify(spaceStatusCheckerCase).assertSpaceExistsAndActive(SPACE_UUID);
    }
}
