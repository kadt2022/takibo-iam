package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.rbac.model.UserGovernanceRoleAssignment;
import com.takibo.identitycore.infrastructure.entity.UserRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserGovernanceRoleRepositoryAdapterTest {

    private static final UUID ORG_ID   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID  = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ROLE_ID  = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private JpaUserRoleRepository jpa;

    @InjectMocks
    private UserGovernanceRoleRepositoryAdapter adapter;

    @Test
    void existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId_delegatesToJpa() {
        when(jpa.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(ORG_ID, SPACE_ID, USER_ID, ROLE_ID))
                .thenReturn(true);

        boolean result = adapter.existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId(
                ORG_ID, SPACE_ID, USER_ID, ROLE_ID);

        assertThat(result).isTrue();
        verify(jpa).existsByOrgIdAndSpaceIdAndUserIdAndRoleId(ORG_ID, SPACE_ID, USER_ID, ROLE_ID);
    }

    @Test
    void saveAll_mapsToUserRoleEntityAndFlushes() {
        Instant assignedAt = Instant.parse("2026-01-01T00:00:00Z");
        UserGovernanceRoleAssignment assignment =
                new UserGovernanceRoleAssignment(ORG_ID, SPACE_ID, USER_ID, ROLE_ID, assignedAt);

        adapter.saveAll(List.of(assignment));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRoleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpa).saveAllAndFlush(captor.capture());

        UserRoleEntity entity = captor.getValue().get(0);
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(entity.getRoleId()).isEqualTo(ROLE_ID);
        assertThat(entity.getAssignedAt()).isEqualTo(assignedAt);
    }

    @Test
    void saveAll_dataIntegrityViolation_allNowExist_isSwallowed() {
        UserGovernanceRoleAssignment assignment =
                new UserGovernanceRoleAssignment(ORG_ID, SPACE_ID, USER_ID, ROLE_ID, Instant.now());

        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(jpa).saveAllAndFlush(any());
        when(jpa.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(ORG_ID, SPACE_ID, USER_ID, ROLE_ID))
                .thenReturn(true);

        // ne doit pas lever d'exception
        adapter.saveAll(List.of(assignment));
    }

    @Test
    void saveAll_dataIntegrityViolation_someStillMissing_rethrows() {
        UserGovernanceRoleAssignment assignment =
                new UserGovernanceRoleAssignment(ORG_ID, SPACE_ID, USER_ID, ROLE_ID, Instant.now());

        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate");
        doThrow(cause).when(jpa).saveAllAndFlush(any());
        when(jpa.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(ORG_ID, SPACE_ID, USER_ID, ROLE_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> adapter.saveAll(List.of(assignment)))
                .isSameAs(cause);
    }
}
