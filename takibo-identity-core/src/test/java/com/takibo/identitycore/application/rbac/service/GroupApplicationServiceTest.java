package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.GroupNature;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupApplicationServiceTest {

    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID GROUP_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final SpaceId SPACE_ID = SpaceId.of(SPACE_UUID);

    @Mock private GroupRepository groupRepository;
    @Mock private SpaceStatusCheckerCase spaceStatusCheckerCase;

    @InjectMocks
    private GroupApplicationService service;

    @Test
    void ensure_groupDoesNotExist_savesWithGovernanceNature() {
        when(groupRepository.findBySpaceIdAndCode(SPACE_ID, "G_ADMINS")).thenReturn(Optional.empty());
        when(groupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.ensure(SPACE_ID, "G_ADMINS", "Admins", "Admin group");

        ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(captor.capture());

        assertThat(captor.getValue().getNature()).isEqualTo(GroupNature.GOVERNANCE);
        assertThat(captor.getValue().getCode()).isEqualTo("G_ADMINS");
    }

    @Test
    void ensureGroup_groupDoesNotExist_savesWithGovernanceNatureAndReturnsId() {
        Group saved = Group.builder()
                .id(GroupId.of(GROUP_UUID)).spaceId(SPACE_ID)
                .nature(GroupNature.GOVERNANCE)
                .code("G_OPS").name("G_OPS")
                .createdAt(Instant.now()).updatedAt(Instant.now()).version(0L)
                .build();

        when(groupRepository.findIdBySpaceIdAndCode(SPACE_ID, "G_OPS")).thenReturn(Optional.empty());
        when(groupRepository.save(any())).thenReturn(saved);

        UUID result = service.ensureGroup(SPACE_UUID, "G_OPS", null, null);

        assertThat(result).isEqualTo(GROUP_UUID);

        ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getNature()).isEqualTo(GroupNature.GOVERNANCE);
    }
}
