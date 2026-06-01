package com.takibo.identitycore.domain.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.domain.security.PasswordHashingComponent;
import com.takibo.identitycore.domain.security.policy.password.PasswordPolicyValidator;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountDomainService {

    private final AccountRepository accountRepository;
    private final AccountCredentialsRepository credentialsRepository;
    private final TakiboIdentityRepository takiboIdentityRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordHashingComponent passwordHashingComponent;
    private final Clock clock;

    public Account resolveAccountForRegistration(CreateUserCommand command, UUID organizationId) {
        return Optional.ofNullable(command.accountId())
                .map(accountId -> resolveExistingAccount(accountId, organizationId))
                .orElseGet(() -> provisionNewAccount(command, organizationId));
    }

    public Account provisionAccountWithCredentials(
            UUID organizationId,
            String email,
            String rawPassword,
            Map<String, Object> metadata
    ) {
        EmailAddress emailAddress = new EmailAddress(email);
        validateEmailUniqueness(emailAddress);
        passwordPolicyValidator.validatePasswordCompliance(rawPassword);

        Account account = createAccountEntity(organizationId, emailAddress, metadata);
        provisionAccountCredentials(account, rawPassword);

        provisionTakiboIdentity(account);

        return account;
    }

    private Account resolveExistingAccount(UUID accountId, UUID organizationId) {
        AccountId id = AccountId.of(accountId);

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new UserCreationException("Account not found: " + accountId));

        validateAccountOrganizationAlignment(account, organizationId);

        ensureTakiboIdentityExists(account);

        return account;
    }

    private Account provisionNewAccount(CreateUserCommand command, UUID organizationId) {
        if (command.email() == null || command.rawPassword() == null) {
            throw new UserCreationException("Either accountId OR (email + rawPassword) must be provided");
        }

        return provisionAccountWithCredentials(
                organizationId,
                command.email(),
                command.rawPassword(),
                command.metadata()
        );
    }

    private Account createAccountEntity(UUID organizationId, EmailAddress email, Map<String, Object> metadata) {
        Account account = Account.create(email, null, null, metadata).withOrgId(organizationId);
        return accountRepository.save(account);
    }

    private void provisionAccountCredentials(Account account, String rawPassword) {
        PasswordHash hash = passwordHashingComponent.hashPassword(rawPassword);

        AccountCredentials credentials = AccountCredentials.builder()
                .accountId(account.getId())
                .passwordHash(hash)
                .passwordUpdatedAt(clock.instant())
                .mustChangeNextLogin(false)
                .failedAttempts(0)
                .lockedUntil(null)
                .createdAt(clock.instant())
                .updatedAt(clock.instant())
                .version(0L)
                .build();

        credentialsRepository.save(credentials, account.getOrgId());
    }

    private void provisionTakiboIdentity(Account account) {
        // Ne créer que si n'existe pas déjà
        if (!takiboIdentityRepository.existsByOrgIdAndAccountId(account.getOrgId(), account.getId().getValue())) {
            TakiboIdentity identity = TakiboIdentity.createHuman(
                    TakiboIdentityId.of(account.getId().getValue()),  // identity_id = account_id
                    OrganizationId.of(account.getOrgId()),
                    account.getId()
            );
           takiboIdentityRepository.save(identity);

            //le flush immédiat
            takiboIdentityRepository.flush();
        }
    }

    private void ensureTakiboIdentityExists(Account account) {
        if (!takiboIdentityRepository.existsByOrgIdAndAccountId(account.getOrgId(), account.getId().getValue())) {
            provisionTakiboIdentity(account);
        }
    }

    private void validateEmailUniqueness(EmailAddress email) {
        if (accountRepository.findByEmail(email).isPresent()) {
            throw new UserCreationException(
                    "Email already registered. Provide accountId or authenticate to link accounts."
            );
        }
    }

    private void validateAccountOrganizationAlignment(Account account, UUID expectedOrganizationId) {
        if (!account.getOrgId().equals(expectedOrganizationId)) {
            throw new UserCreationException(
                    "Account organization misalignment. Account belongs to %s, but port requires %s"
                            .formatted(account.getOrgId(), expectedOrganizationId)
            );
        }
    }
}