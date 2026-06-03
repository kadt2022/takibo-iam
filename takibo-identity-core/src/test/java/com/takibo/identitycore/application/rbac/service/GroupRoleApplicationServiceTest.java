package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GroupRoleApplicationServiceTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID GROUP_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ROLE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private GroupRoleRepository groupRoleRepository;

    @InjectMocks
    private GroupRoleApplicationService service;

    @Test
    void ensureGroupHasRole_createsDomainGroupRoleAndDelegatesToRepository() {
        service.ensureGroupHasRole(SPACE_ID, GROUP_ID, ROLE_ID);

        ArgumentCaptor<GroupRole> captor = ArgumentCaptor.forClass(GroupRole.class);
        verify(groupRoleRepository).save(captor.capture());

        GroupRole groupRole = captor.getValue();
        assertThat(groupRole.getSpaceId().value()).isEqualTo(SPACE_ID);
        assertThat(groupRole.getGroupId().value()).isEqualTo(GROUP_ID);
        assertThat(groupRole.getRoleId().value()).isEqualTo(ROLE_ID);
    }
}
