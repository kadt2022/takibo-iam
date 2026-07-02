package com.takibo.identitycore.domain.repository;


import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;

import java.util.Optional;
import java.util.UUID;


public interface AccountCredentialsRepository {
    AccountCredentials save(AccountCredentials credentials, UUID orgId);

    Optional<AccountCredentials> find(OrganizationId orgId, AccountId accountId);
}
