package com.takibo.identitycore.domain.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import com.takibo.identitycore.domain.security.PasswordHashingComponent;
import com.takibo.identitycore.domain.security.policy.password.PasswordPolicyValidator;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDomainServiceTest {

    private static final UUID ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OTHER_ORG  = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private AccountRepository            accountRepository;
    @Mock private AccountCredentialsRepository credentialsRepository;
    @Mock private TakiboIdentityRepository     takiboIdentityRepository;
    @Mock private PasswordPolicyValidator      passwordPolicyValidator;
    @Mock private PasswordHashingComponent     passwordHashingComponent;
    @Mock private Clock                        clock;

    @InjectMocks
    private AccountDomainService service;

    // ─── resolveAccountForRegistration ────────────────────────────────────────

    @Test
    void resolveAccountForRegistration_withAccountId_routesToExistingAccount() {
        Account existing = mockAccount(ACCOUNT_ID, ORG_ID);
        when(accountRepository.findById(AccountId.of(ACCOUNT_ID))).thenReturn(Optional.of(existing));
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(true);

        Account result = service.resolveAccountForRegistration(commandWithAccountId(ACCOUNT_ID), ORG_ID);

        assertThat(result).isSameAs(existing);
        verify(accountRepository).findById(AccountId.of(ACCOUNT_ID));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void resolveAccountForRegistration_withoutAccountId_provisionsNewAccount() {
        Account saved = mockAccount(ACCOUNT_ID, ORG_ID);
        when(accountRepository.findByEmail(any(OrganizationId.class), any(EmailAddress.class)))
                .thenReturn(Optional.empty());
        doNothing().when(passwordPolicyValidator).validatePasswordCompliance(any());
        when(passwordHashingComponent.hashPassword(any())).thenReturn(mock(PasswordHash.class));
        when(accountRepository.save(any())).thenReturn(saved);
        when(clock.instant()).thenReturn(Instant.now());
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(false);
        when(takiboIdentityRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Account result = service.resolveAccountForRegistration(commandWithEmail("new@example.com", "P@ssword1"), ORG_ID);

        assertThat(result).isSameAs(saved);
        verify(accountRepository).save(any());
    }

    // ─── resolveExistingAccount ────────────────────────────────────────────────

    @Test
    void resolveExistingAccount_accountNotFound_throws() {
        when(accountRepository.findById(AccountId.of(ACCOUNT_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAccountForRegistration(commandWithAccountId(ACCOUNT_ID), ORG_ID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void resolveExistingAccount_orgMismatch_throws() {
        Account wrongOrgAccount = mockAccount(ACCOUNT_ID, OTHER_ORG);
        when(accountRepository.findById(AccountId.of(ACCOUNT_ID))).thenReturn(Optional.of(wrongOrgAccount));

        assertThatThrownBy(() -> service.resolveAccountForRegistration(commandWithAccountId(ACCOUNT_ID), ORG_ID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("organization misalignment");
    }

    @Test
    void resolveExistingAccount_identityAlreadyExists_doesNotCreateIt() {
        Account existing = mockAccount(ACCOUNT_ID, ORG_ID);
        when(accountRepository.findById(AccountId.of(ACCOUNT_ID))).thenReturn(Optional.of(existing));
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(true);

        service.resolveAccountForRegistration(commandWithAccountId(ACCOUNT_ID), ORG_ID);

        verify(takiboIdentityRepository, never()).save(any());
    }

    @Test
    void resolveExistingAccount_identityMissing_createsIt() {
        Account existing = mockAccount(ACCOUNT_ID, ORG_ID);
        when(accountRepository.findById(AccountId.of(ACCOUNT_ID))).thenReturn(Optional.of(existing));
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(false);
        when(takiboIdentityRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolveAccountForRegistration(commandWithAccountId(ACCOUNT_ID), ORG_ID);

        verify(takiboIdentityRepository).save(any(TakiboIdentity.class));
        verify(takiboIdentityRepository).flush();
    }

    // ─── Phase A contract — identity_id == account_id ─────────────────────────

    @Test
    void provisionTakiboIdentity_phaseA_identityIdEqualsAccountId() {
        Account account = mockAccount(ACCOUNT_ID, ORG_ID);
        when(accountRepository.findByEmail(any(OrganizationId.class), any(EmailAddress.class)))
                .thenReturn(Optional.empty());
        doNothing().when(passwordPolicyValidator).validatePasswordCompliance(any());
        when(passwordHashingComponent.hashPassword(any())).thenReturn(mock(PasswordHash.class));
        when(accountRepository.save(any())).thenReturn(account);
        when(clock.instant()).thenReturn(Instant.now());
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(false);
        when(takiboIdentityRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.provisionAccountWithCredentials(ORG_ID, "user@example.com", "P@ssword1", Map.of());

        ArgumentCaptor<TakiboIdentity> captor = ArgumentCaptor.forClass(TakiboIdentity.class);
        verify(takiboIdentityRepository).save(captor.capture());

        TakiboIdentity identity = captor.getValue();
        assertThat(identity.getId().getValue())
                .as("Phase A : TakiboIdentity.identityId doit être égal à Account.id")
                .isEqualTo(ACCOUNT_ID);
        assertThat(identity.getAccountId().getValue()).isEqualTo(ACCOUNT_ID);
        assertThat(identity.getOrgId().getValue()).isEqualTo(ORG_ID);
    }

    @Test
    void provisionTakiboIdentity_ifAlreadyExists_doesNotCreateDuplicate() {
        Account account = mockAccount(ACCOUNT_ID, ORG_ID);
        when(accountRepository.findByEmail(any(OrganizationId.class), any(EmailAddress.class)))
                .thenReturn(Optional.empty());
        doNothing().when(passwordPolicyValidator).validatePasswordCompliance(any());
        when(passwordHashingComponent.hashPassword(any())).thenReturn(mock(PasswordHash.class));
        when(accountRepository.save(any())).thenReturn(account);
        when(clock.instant()).thenReturn(Instant.now());
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(ORG_ID, ACCOUNT_ID)).thenReturn(true);

        service.provisionAccountWithCredentials(ORG_ID, "user@example.com", "P@ssword1", Map.of());

        verify(takiboIdentityRepository, never()).save(any());
        verify(takiboIdentityRepository, never()).flush();
    }

    // ─── validateEmailUniqueness — org-scoped ─────────────────────────────────

    @Test
    void provisionAccountWithCredentials_duplicateEmail_sameOrg_throws() {
        when(accountRepository.findByEmail(eq(OrganizationId.of(ORG_ID)), any(EmailAddress.class)))
                .thenReturn(Optional.of(mock(Account.class)));

        assertThatThrownBy(() ->
                service.provisionAccountWithCredentials(ORG_ID, "taken@example.com", "P@ssword1", Map.of()))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("Email already registered in this organization");

        verify(accountRepository, never()).save(any());
        verify(takiboIdentityRepository, never()).save(any());
    }

    @Test
    void provisionAccountWithCredentials_sameEmail_differentOrg_succeeds() {
        Account saved = mockAccount(ACCOUNT_ID, OTHER_ORG);
        when(accountRepository.findByEmail(eq(OrganizationId.of(OTHER_ORG)), any(EmailAddress.class)))
                .thenReturn(Optional.empty());
        doNothing().when(passwordPolicyValidator).validatePasswordCompliance(any());
        when(passwordHashingComponent.hashPassword(any())).thenReturn(mock(PasswordHash.class));
        when(accountRepository.save(any())).thenReturn(saved);
        when(clock.instant()).thenReturn(Instant.now());
        when(takiboIdentityRepository.existsByOrgIdAndAccountId(OTHER_ORG, ACCOUNT_ID)).thenReturn(false);
        when(takiboIdentityRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Account result = service.provisionAccountWithCredentials(OTHER_ORG, "shared@example.com", "P@ssword1", Map.of());

        assertThat(result).isSameAs(saved);
        verify(accountRepository).findByEmail(eq(OrganizationId.of(OTHER_ORG)), any(EmailAddress.class));
        verify(accountRepository, never()).findByEmail(any(EmailAddress.class));
    }

    // ─── provisionNewAccount guards ───────────────────────────────────────────

    @Test
    void resolveAccountForRegistration_missingEmail_throws() {
        CreateUserCommand command = CreateUserCommand.builder()
                .spaceId(UUID.randomUUID())
                .rawPassword("P@ssword1")
                .build();

        assertThatThrownBy(() -> service.resolveAccountForRegistration(command, ORG_ID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("email + rawPassword");
    }

    @Test
    void resolveAccountForRegistration_missingPassword_throws() {
        CreateUserCommand command = CreateUserCommand.builder()
                .spaceId(UUID.randomUUID())
                .email("user@example.com")
                .build();

        assertThatThrownBy(() -> service.resolveAccountForRegistration(command, ORG_ID))
                .isInstanceOf(UserCreationException.class)
                .hasMessageContaining("email + rawPassword");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Account mockAccount(UUID accountId, UUID orgId) {
        Account account = mock(Account.class);
        lenient().when(account.getId()).thenReturn(AccountId.of(accountId));
        when(account.getOrgId()).thenReturn(orgId);
        return account;
    }

    private CreateUserCommand commandWithAccountId(UUID accountId) {
        return CreateUserCommand.builder()
                .spaceId(UUID.randomUUID())
                .accountId(accountId)
                .build();
    }

    private CreateUserCommand commandWithEmail(String email, String password) {
        return CreateUserCommand.builder()
                .spaceId(UUID.randomUUID())
                .email(email)
                .rawPassword(password)
                .businessRoleCodes(List.of())
                .build();
    }
}
