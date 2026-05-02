package com.takibo.identitycore.domain.repository;


import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.vo.OrganizationId;

import java.util.Optional;


public interface AccountRepository {
    Optional<Account> findById(AccountId id);

    Optional<Account> findByEmail(EmailAddress email);

    Optional<Account> findByEmail(OrganizationId orgId, EmailAddress email);

    Account save(Account account);
}