package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.AccountJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRepositoryAdapterTest {

    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock
    private JpaAccountRepository jpa;

    private final AccountJpaMapper mapper = Mappers.getMapper(AccountJpaMapper.class);

    private AccountRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountRepositoryAdapter(jpa, mapper);
    }

    private static AccountEntity entity() {
        AccountEntity entity = new AccountEntity();
        entity.setId(ACCOUNT_UUID);
        entity.setOrgId(ORG_UUID);
        entity.setEmail("jane@example.com");
        return entity;
    }

    @Test
    void organizationScopedLookups_neverFallBackToAGlobalSearch() {
        when(jpa.findByIdAndOrgId(ACCOUNT_UUID, ORG_UUID)).thenReturn(Optional.empty());
        when(jpa.findByOrgIdAndEmailIgnoreCase(ORG_UUID, "jane@example.com")).thenReturn(Optional.of(entity()));

        assertThat(adapter.findById(OrganizationId.of(ORG_UUID), AccountId.of(ACCOUNT_UUID))).isEmpty();
        Optional<Account> byEmail = adapter.findByEmail(OrganizationId.of(ORG_UUID), new EmailAddress("Jane@Example.com"));

        assertThat(byEmail).get().satisfies(account -> {
            assertThat(account.getId()).isEqualTo(AccountId.of(ACCOUNT_UUID));
            assertThat(account.getOrgId()).isEqualTo(ORG_UUID);
        });
        verify(jpa, never()).findById(any());
        verify(jpa, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void globalLookups_mapTheEntity() {
        when(jpa.findById(ACCOUNT_UUID)).thenReturn(Optional.of(entity()));
        when(jpa.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.empty());

        assertThat(adapter.findById(AccountId.of(ACCOUNT_UUID))).get()
                .extracting(Account::getEmail).isEqualTo(new EmailAddress("jane@example.com"));
        assertThat(adapter.findByEmail(new EmailAddress("jane@example.com"))).isEmpty();
    }

    @Test
    void save_persistsTheMappedEntity_andReturnsThePersistedState() {
        Account account = Account.create(new EmailAddress("jane@example.com"), "Jane", null, null).withOrgId(ORG_UUID);
        when(jpa.save(any(AccountEntity.class))).thenAnswer(i -> i.getArgument(0));

        Account saved = adapter.save(account);

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(jpa).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG_UUID);
        assertThat(captor.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getId()).isEqualTo(account.getId());
        assertThat(saved.getDisplayName()).isEqualTo("Jane");
    }
}
