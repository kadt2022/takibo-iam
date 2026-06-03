package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.rbac.model.UserGroupMembership;
import com.takibo.identitycore.infrastructure.entity.GroupMemberEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGroupMembershipRepositoryAdapterTest {

    private static final UUID ORG_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID    = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID     = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID GROUP_ID    = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID ASSIGNED_BY = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private JpaGroupMemberRepository jpaGroupMemberRepository;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks
    private UserGroupMembershipRepositoryAdapter adapter;

    @Test
    void findExistingGroupIds_delegatesToJpaRepository() {
        Set<UUID> groupIds = Set.of(GROUP_ID);
        when(jpaGroupMemberRepository.findExistingGroupIds(ORG_ID, SPACE_ID, USER_ID, groupIds))
                .thenReturn(groupIds);

        Set<UUID> result = adapter.findExistingGroupIds(ORG_ID, SPACE_ID, USER_ID, groupIds);

        assertThat(result).containsExactly(GROUP_ID);
        verify(jpaGroupMemberRepository).findExistingGroupIds(ORG_ID, SPACE_ID, USER_ID, groupIds);
    }

    @Test
    void saveAllIdempotently_mapsMembershipsToEntitiesAndFlushes() {
        allowTransactions();
        UserGroupMembership membership =
                new UserGroupMembership(ORG_ID, SPACE_ID, USER_ID, GROUP_ID, ASSIGNED_AT, ASSIGNED_BY.toString());

        adapter.saveAllIdempotently(List.of(membership));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupMemberEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpaGroupMemberRepository).saveAllAndFlush(captor.capture());

        GroupMemberEntity entity = captor.getValue().get(0);
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(entity.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(entity.getAssignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(entity.getAssignedBy()).isEqualTo(ASSIGNED_BY);
    }

    @Test
    void saveAllIdempotently_concurrentDuplicateIsIgnoredIfMembershipNowExists() {
        allowTransactions();
        UserGroupMembership membership =
                new UserGroupMembership(ORG_ID, SPACE_ID, USER_ID, GROUP_ID, ASSIGNED_AT, null);
        List<UserGroupMembership> memberships = List.of(membership);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(jpaGroupMemberRepository).saveAllAndFlush(any());
        when(jpaGroupMemberRepository.existsByOrgIdAndSpaceIdAndUserIdAndGroupId(ORG_ID, SPACE_ID, USER_ID, GROUP_ID))
                .thenReturn(true);

        assertThatNoException().isThrownBy(() -> adapter.saveAllIdempotently(memberships));
    }

    @Test
    void saveAllIdempotently_nonIdempotentConflictIsPropagated() {
        allowTransactions();
        UserGroupMembership membership =
                new UserGroupMembership(ORG_ID, SPACE_ID, USER_ID, GROUP_ID, ASSIGNED_AT, null);
        List<UserGroupMembership> memberships = List.of(membership);
        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate");
        doThrow(cause).when(jpaGroupMemberRepository).saveAllAndFlush(any());
        when(jpaGroupMemberRepository.existsByOrgIdAndSpaceIdAndUserIdAndGroupId(ORG_ID, SPACE_ID, USER_ID, GROUP_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> adapter.saveAllIdempotently(memberships))
                .isSameAs(cause);
    }

    private void allowTransactions() {
        when(transactionManager.getTransaction(any()))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
    }
}
