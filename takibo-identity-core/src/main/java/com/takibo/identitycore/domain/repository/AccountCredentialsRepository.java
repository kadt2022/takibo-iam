package com.takibo.identitycore.domain.repository;


import com.takibo.identitycore.domain.model.AccountCredentials;

import java.util.UUID;


public interface AccountCredentialsRepository {
    AccountCredentials save(AccountCredentials credentials, UUID orgId);
}