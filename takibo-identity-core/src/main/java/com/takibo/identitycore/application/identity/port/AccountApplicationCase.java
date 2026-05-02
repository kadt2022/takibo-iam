package com.takibo.identitycore.application.identity.port;

import com.takibo.identitycore.interfaces.rest.response.AccountResponse;

import java.util.UUID;

public interface AccountApplicationCase {
    AccountResponse createAccountNative(String email, String rawPassword);
    AccountResponse createAccountInOrg(UUID orgId, String emailStr, String rawPassword);
}
