package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import com.takibo.identitycore.infrastructure.entity.TakiboIdentityEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.TakiboIdentityJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaTakiboIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TakiboIdentityRepositoryAdapterTest {

    private static final UUID ORG_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID  = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID IDENTITY_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private JpaTakiboIdentityRepository jpaRepository;
    @Mock private TakiboIdentityJpaMapper mapper;

    @InjectMocks
    private TakiboIdentityRepositoryAdapter adapter;

    @Test
    void lockAndFindIdentityId_entityFound_returnsIdentityId() {
        TakiboIdentityEntity entity = TakiboIdentityEntity.builder()
                .identityId(IDENTITY_ID)
                .orgId(ORG_ID)
                .accountId(ACCOUNT_ID)
                .build();

        when(jpaRepository.lockByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(entity));

        Optional<UUID> result = adapter.lockAndFindIdentityIdByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID);

        assertThat(result).contains(IDENTITY_ID);
        verify(jpaRepository).lockByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID);
    }

    @Test
    void lockAndFindIdentityId_entityNotFound_returnsEmpty() {
        when(jpaRepository.lockByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        Optional<UUID> result = adapter.lockAndFindIdentityIdByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void save_flushesTheMappedEntity_soConstraintViolationsSurfaceInTheCaller() {
        TakiboIdentity identity = TakiboIdentity.createHuman(
                TakiboIdentityId.of(IDENTITY_ID), OrganizationId.of(ORG_ID), AccountId.of(ACCOUNT_ID));
        TakiboIdentityEntity entity = TakiboIdentityEntity.builder().identityId(IDENTITY_ID).build();
        when(mapper.toEntity(identity)).thenReturn(entity);

        assertThat(adapter.save(identity)).isSameAs(identity);

        verify(jpaRepository).saveAndFlush(entity);
    }

    @Test
    void flush_delegates() {
        adapter.flush();

        verify(jpaRepository).flush();
    }

    @Test
    void lookups_areScopedToTheOrganizationAndAccount() {
        TakiboIdentityEntity entity = TakiboIdentityEntity.builder().identityId(IDENTITY_ID).build();
        TakiboIdentity identity = TakiboIdentity.createHuman(
                TakiboIdentityId.of(IDENTITY_ID), OrganizationId.of(ORG_ID), AccountId.of(ACCOUNT_ID));
        when(jpaRepository.findById(IDENTITY_ID)).thenReturn(Optional.of(entity));
        when(jpaRepository.findByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(Optional.of(entity));
        when(jpaRepository.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(true);
        when(mapper.toDomain(entity)).thenReturn(identity);

        assertThat(adapter.findById(TakiboIdentityId.of(IDENTITY_ID))).containsSame(identity);
        assertThat(adapter.findByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).containsSame(identity);
        assertThat(adapter.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).isTrue();
    }
}
