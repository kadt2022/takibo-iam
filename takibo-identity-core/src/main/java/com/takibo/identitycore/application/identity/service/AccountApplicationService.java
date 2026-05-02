package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.port.AccountApplicationCase;
import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.domain.security.policy.password.PasswordPolicy;
import com.takibo.identitycore.domain.exception.PasswordPolicyViolationException;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.interfaces.rest.response.AccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountApplicationService implements AccountApplicationCase {

    private final AccountRepository accountRepository;
    private final AccountCredentialsRepository accountCredentialsRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasherCase passwordHasherCase;

    @Override
    @Transactional
    public AccountResponse createAccountNative(String emailStr, String rawPassword) {
        if (!passwordPolicy.isValid(rawPassword)) {
            throw new PasswordPolicyViolationException("Password does not meet policy");
        }
        EmailAddress email = new EmailAddress(emailStr);

        Account account = accountRepository.findByEmail(email)
                .orElseGet(() -> {
                    Account newAccount = Account.create(email, null, null, Map.of());
                    Account accountCreated = accountRepository.save(newAccount);
                    PasswordHash pwd = PasswordHash.of(passwordHasherCase.hash(rawPassword), "bcrypt", 1);

                    AccountCredentials accountCredentials = AccountCredentials.createNew(accountCreated.getId(), pwd);
                    accountCredentialsRepository.save(accountCredentials, accountCreated.getOrgId());

                    return accountCreated;
                });

        return new AccountResponse(account.getId().getValue(), account.getEmail().value());
    }

    @Override
    @Transactional
    public AccountResponse createAccountInOrg(UUID orgId, String emailStr, String rawPassword) {
        if (!passwordPolicy.isValid(rawPassword)) {
            throw new PasswordPolicyViolationException("Password does not meet policy");
        }

        EmailAddress email = new EmailAddress(emailStr);

        Optional<Account> optionalAccount = accountRepository.findByEmail(new OrganizationId(orgId), email);
        if (optionalAccount.isPresent()) {
            Account account = optionalAccount.get();
            return new AccountResponse(account.getId().getValue(), account.getEmail().value());
        }

        Account newAccount = Account.create(email, null, null, Map.of())
                .withOrgId(orgId);

        Account accountCreated = accountRepository.save(newAccount);

        PasswordHash pwd = PasswordHash.of(passwordHasherCase.hash(rawPassword), "bcrypt", 1);
        AccountCredentials accountCredentials = AccountCredentials.createNew(accountCreated.getId(), pwd);
        accountCredentialsRepository.save(accountCredentials, accountCreated.getOrgId());

        return new AccountResponse(accountCreated.getId().getValue(), accountCreated.getEmail().value());
    }
}
