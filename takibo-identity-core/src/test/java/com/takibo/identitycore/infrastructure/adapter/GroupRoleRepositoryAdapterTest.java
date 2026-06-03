package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupRoleJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRolesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupRoleRepositoryAdapterTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID GROUP_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ROLE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private GroupRoleJpaMapper mapper;
    @Mock private JpaGroupRolesRepository jpa;

    @InjectMocks
    private GroupRoleRepositoryAdapter adapter;

    @Test
    void save_existingGroupRole_returnsWithoutWriting() {
        GroupRole groupRole = groupRole();
        when(jpa.existsBySpaceIdAndGroupIdAndRoleId(SPACE_ID, GROUP_ID, ROLE_ID))
                .thenReturn(true);

        GroupRole result = adapter.save(groupRole);

        assertThat(result).isSameAs(groupRole);
        verify(mapper, never()).toEntity(groupRole);
    }

    @Test
    void save_newGroupRole_mapsAndFlushes() {
        GroupRole groupRole = groupRole();
        GroupRoleEntity entity = new GroupRoleEntity();
        GroupRole saved = groupRole();
        when(jpa.existsBySpaceIdAndGroupIdAndRoleId(SPACE_ID, GROUP_ID, ROLE_ID))
                .thenReturn(false);
        when(mapper.toEntity(groupRole)).thenReturn(entity);
        when(jpa.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(saved);

        GroupRole result = adapter.save(groupRole);

        assertThat(result).isSameAs(saved);
        verify(jpa).saveAndFlush(entity);
    }

    @Test
    void save_concurrentDuplicateIsIgnoredIfGroupRoleNowExists() {
        GroupRole groupRole = groupRole();
        GroupRoleEntity entity = new GroupRoleEntity();
        when(jpa.existsBySpaceIdAndGroupIdAndRoleId(SPACE_ID, GROUP_ID, ROLE_ID))
                .thenReturn(false, true);
        when(mapper.toEntity(groupRole)).thenReturn(entity);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(jpa).saveAndFlush(entity);

        GroupRole result = adapter.save(groupRole);

        assertThat(result).isSameAs(groupRole);
    }

    @Test
    void save_nonIdempotentConflictIsPropagated() {
        GroupRole groupRole = groupRole();
        GroupRoleEntity entity = new GroupRoleEntity();
        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate");
        when(jpa.existsBySpaceIdAndGroupIdAndRoleId(SPACE_ID, GROUP_ID, ROLE_ID))
                .thenReturn(false, false);
        when(mapper.toEntity(groupRole)).thenReturn(entity);
        doThrow(cause).when(jpa).saveAndFlush(entity);

        assertThatThrownBy(() -> adapter.save(groupRole))
                .isSameAs(cause);
    }

    private GroupRole groupRole() {
        return GroupRole.create(
                SpaceId.of(SPACE_ID),
                GroupId.of(GROUP_ID),
                RoleId.of(ROLE_ID)
        );
    }
}
