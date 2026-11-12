package com.takibo.identitycore.infrastructure.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceAdministrationRepositoryAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private EntityManager em;
    @Mock private Query query;
    @InjectMocks private SpaceAdministrationRepositoryAdapter adapter;

    @Test
    void the_lock_is_a_transaction_advisory_lock_keyed_by_the_space() {
        when(em.createNativeQuery(contains("pg_advisory_xact_lock"))).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);

        adapter.lockAdministrationOf(SPACE_ID);

        verify(query).setParameter("key", "takibo:space-administration:" + SPACE_ID);
        verify(query).getSingleResult();
    }

    @Test
    void an_administrator_is_recognised_by_the_same_rule() {
        when(em.createNativeQuery(contains("u.account_id = :accountId"))).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);

        assertThat(adapter.isEffectiveAdministrator(ORG_ID, SPACE_ID, ACCOUNT_ID)).isTrue();
    }

    @Test
    void the_count_excludes_the_target_and_reads_both_paths() {
        when(em.createNativeQuery(contains("G_SPACE_ADMINS"))).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(2L);

        assertThat(adapter.countEffectiveAdministratorsOtherThan(ORG_ID, SPACE_ID, ACCOUNT_ID)).isEqualTo(2L);

        verify(query).setParameter("accountId", ACCOUNT_ID);
        verify(query).setParameter("spaceId", SPACE_ID);
    }
}
