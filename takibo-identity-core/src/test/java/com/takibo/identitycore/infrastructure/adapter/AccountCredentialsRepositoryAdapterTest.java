package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.infrastructure.entity.AccountCredentialsEntity;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.AccountCredentialsJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaAccountCredentialsRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountCredentialsRepositoryAdapterTest {

    private static final UUID ORG_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private JpaAccountCredentialsRepository jpa;
    @Mock private AccountCredentialsJpaMapper mapper;
    @Mock private EntityManager em;

    @InjectMocks
    private AccountCredentialsRepositoryAdapter adapter;

    @Test
    void save_setsAccountReferenceOrgIdAndAccountId() {
        AccountId accountId = AccountId.of(ACCOUNT_UUID);
        AccountCredentials domain = AccountCredentials.builder()
                .accountId(accountId)
                .passwordHash(PasswordHash.of("$2a$hash", "bcrypt", 1))
                .failedAttempts(0)
                .mustChangeNextLogin(false)
                .build();

        AccountCredentialsEntity entity = new AccountCredentialsEntity();
        AccountEntity accountRef = mock(AccountEntity.class);
        AccountCredentials expectedResult = mock(AccountCredentials.class);

        when(mapper.toEntity(domain, ORG_ID)).thenReturn(entity);
        when(em.getReference(AccountEntity.class, ACCOUNT_UUID)).thenReturn(accountRef);
        when(jpa.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(expectedResult);

        AccountCredentials result = adapter.save(domain, ORG_ID);

        assertThat(result).isSameAs(expectedResult);
        assertThat(entity.getAccount()).isSameAs(accountRef);
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getAccountId()).isEqualTo(ACCOUNT_UUID);

        verify(em).getReference(AccountEntity.class, ACCOUNT_UUID);
        verify(jpa).saveAndFlush(entity);
        verify(mapper).toDomain(entity);
    }

    @Test
    void save_usesGetReferenceNotFind() {
        AccountId accountId = AccountId.of(ACCOUNT_UUID);
        AccountCredentials domain = AccountCredentials.builder()
                .accountId(accountId)
                .passwordHash(PasswordHash.of("$2a$hash", "bcrypt", 1))
                .failedAttempts(0)
                .mustChangeNextLogin(false)
                .build();

        AccountCredentialsEntity entity = new AccountCredentialsEntity();
        when(mapper.toEntity(domain, ORG_ID)).thenReturn(entity);
        when(em.getReference(AccountEntity.class, ACCOUNT_UUID)).thenReturn(mock(AccountEntity.class));
        when(jpa.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(mock(AccountCredentials.class));

        adapter.save(domain, ORG_ID);

        verify(em).getReference(AccountEntity.class, ACCOUNT_UUID);
        verify(em, never()).find(eq(AccountEntity.class), any());
    }

    @Test
    void find_existingCredentials_returnsMappedDomain() {
        AccountCredentialsEntity.AccountCredentialsId id =
                new AccountCredentialsEntity.AccountCredentialsId(ORG_ID, ACCOUNT_UUID);
        AccountCredentialsEntity entity = new AccountCredentialsEntity();
        AccountCredentials domain = mock(AccountCredentials.class);
        when(jpa.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<AccountCredentials> result = adapter.find(OrganizationId.of(ORG_ID), AccountId.of(ACCOUNT_UUID));

        assertThat(result).containsSame(domain);
        verify(mapper).toDomain(entity);
    }

    @Test
    void find_missingCredentials_returnsEmpty() {
        AccountCredentialsEntity.AccountCredentialsId id =
                new AccountCredentialsEntity.AccountCredentialsId(ORG_ID, ACCOUNT_UUID);
        when(jpa.findById(id)).thenReturn(Optional.empty());

        Optional<AccountCredentials> result = adapter.find(OrganizationId.of(ORG_ID), AccountId.of(ACCOUNT_UUID));

        assertThat(result).isEmpty();
        verify(mapper, never()).toDomain(any());
    }
}
