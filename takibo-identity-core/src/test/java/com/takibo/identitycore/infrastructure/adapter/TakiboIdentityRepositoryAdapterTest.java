package com.takibo.identitycore.infrastructure.adapter;

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
}
