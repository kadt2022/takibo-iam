package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.domain.exception.PasswordPolicyViolationException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.IdentityStatus;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import com.takibo.identitycore.domain.security.policy.password.PasswordPolicy;
import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.interfaces.rest.response.AccountResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final String RAW_PASSWORD = "Str0ng!Passw0rd";

    @Mock private AccountRepository accountRepository;
    @Mock private AccountCredentialsRepository accountCredentialsRepository;
    @Mock private PasswordPolicy passwordPolicy;
    @Mock private PasswordHasherCase passwordHasherCase;
    @Mock private TakiboIdentityRepository takiboIdentityRepository;

    @InjectMocks
    private AccountApplicationService service;

    @Test
    void weakPassword_isRefusedBeforeAnyLookupOrHashing() {
        when(passwordPolicy.isValid("weak")).thenReturn(false);

        assertThatThrownBy(() -> service.createAccountInOrg(ORG_UUID, "a@b.io", "weak"))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verifyNoInteractions(accountRepository, accountCredentialsRepository, passwordHasherCase);
    }

    @Test
    void existingAccountOfTheOrganization_isReturned_andItsCredentialsAreNeverOverwritten() {
        when(passwordPolicy.isValid(RAW_PASSWORD)).thenReturn(true);
        Account existing = Account.builder()
                .id(AccountId.of(ACCOUNT_UUID)).orgId(ORG_UUID)
                .email(new EmailAddress("jane@example.com"))
                .build();
        when(accountRepository.findByEmail(OrganizationId.of(ORG_UUID), new EmailAddress("jane@example.com")))
                .thenReturn(Optional.of(existing));

        AccountResponse response = service.createAccountInOrg(ORG_UUID, " Jane@Example.com ", RAW_PASSWORD);

        assertThat(response).isEqualTo(new AccountResponse(ACCOUNT_UUID, "jane@example.com"));
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(accountCredentialsRepository, passwordHasherCase);
    }

    @Test
    void newAccount_isCreatedInTheOrganization_withHashedCredentialsOnly() {
        when(passwordPolicy.isValid(RAW_PASSWORD)).thenReturn(true);
        when(accountRepository.findByEmail(any(OrganizationId.class), any(EmailAddress.class)))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordHasherCase.hash(RAW_PASSWORD)).thenReturn("$2a$10$hash");

        AccountResponse response = service.createAccountInOrg(ORG_UUID, "jane@example.com", RAW_PASSWORD);

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(account.capture());
        assertThat(account.getValue().getOrgId()).isEqualTo(ORG_UUID);
        assertThat(account.getValue().getEmail()).isEqualTo(new EmailAddress("jane@example.com"));

        ArgumentCaptor<AccountCredentials> credentials = ArgumentCaptor.forClass(AccountCredentials.class);
        verify(accountCredentialsRepository).save(credentials.capture(), eq(ORG_UUID));
        AccountCredentials saved = credentials.getValue();
        assertThat(saved.getAccountId()).isEqualTo(account.getValue().getId());
        assertThat(saved.getPasswordHash().getHash()).isEqualTo("$2a$10$hash").isNotEqualTo(RAW_PASSWORD);
        assertThat(saved.getPasswordHash().getAlgo()).isEqualTo("bcrypt");
        assertThat(saved.getFailedAttempts()).isZero();
        assertThat(saved.getLockedUntil()).isNull();

        assertThat(response.id()).isEqualTo(account.getValue().getId().getValue());
        assertThat(response.email()).isEqualTo("jane@example.com");
    }

    // ---------- IAM-REVOKE-01 : tout Account humain a son identité, par construction ----------

    @Test
    void a_new_account_receives_exactly_one_active_human_identity_in_the_same_operation() {
        when(passwordPolicy.isValid(RAW_PASSWORD)).thenReturn(true);
        when(accountRepository.findByEmail(any(OrganizationId.class), any(EmailAddress.class)))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordHasherCase.hash(RAW_PASSWORD)).thenReturn("$2a$10$hash");

        AccountResponse response = service.createAccountInOrg(ORG_UUID, "jane@example.com", RAW_PASSWORD);

        ArgumentCaptor<TakiboIdentity> identity = ArgumentCaptor.forClass(TakiboIdentity.class);
        verify(takiboIdentityRepository).save(identity.capture());
        assertThat(identity.getValue().getOrgId()).isEqualTo(OrganizationId.of(ORG_UUID));
        assertThat(identity.getValue().getAccountId().getValue()).isEqualTo(response.id());
        assertThat(identity.getValue().getId().getValue()).isEqualTo(response.id());
        assertThat(identity.getValue().getIdentityType()).isEqualTo(IdentityType.HUMAN);
        assertThat(identity.getValue().getIdentityStatus()).isEqualTo(IdentityStatus.ACTIVE);
    }

    @Test
    void an_existing_account_without_identity_has_it_repaired() {
        when(passwordPolicy.isValid(RAW_PASSWORD)).thenReturn(true);
        when(accountRepository.findByEmail(OrganizationId.of(ORG_UUID), new EmailAddress("jane@example.com")))
                .thenReturn(Optional.of(existingAccount()));
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_UUID, ACCOUNT_UUID)).thenReturn(false);

        service.createAccountInOrg(ORG_UUID, "jane@example.com", RAW_PASSWORD);

        ArgumentCaptor<TakiboIdentity> identity = ArgumentCaptor.forClass(TakiboIdentity.class);
        verify(takiboIdentityRepository).save(identity.capture());
        assertThat(identity.getValue().getAccountId().getValue()).isEqualTo(ACCOUNT_UUID);
        assertThat(identity.getValue().getIdentityStatus()).isEqualTo(IdentityStatus.ACTIVE);
    }

    @Test
    void an_existing_identity_is_never_duplicated_nor_reactivated() {
        when(passwordPolicy.isValid(RAW_PASSWORD)).thenReturn(true);
        when(accountRepository.findByEmail(OrganizationId.of(ORG_UUID), new EmailAddress("jane@example.com")))
                .thenReturn(Optional.of(existingAccount()));
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_UUID, ACCOUNT_UUID)).thenReturn(true);

        service.createAccountInOrg(ORG_UUID, "jane@example.com", RAW_PASSWORD);

        verify(takiboIdentityRepository, never()).save(any());
    }

    private static Account existingAccount() {
        return Account.builder()
                .id(AccountId.of(ACCOUNT_UUID)).orgId(ORG_UUID)
                .email(new EmailAddress("jane@example.com"))
                .build();
    }
}
