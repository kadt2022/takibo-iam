package com.takibo.securitymanagement.sentinel.rule;

import com.takibo.identitycore.domain.exception.AccountLockedException;
import com.takibo.identitycore.domain.exception.InvalidCredentialsException;
import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.UserNotActiveException;
import com.takibo.identitycore.domain.exception.UserNotMemberOfSpaceException;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.securitymanagement.sentinel.advice.SentinelErrorCode;
import com.takibo.securitymanagement.sentinel.advice.SentinelResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelRuleHandlersTest {

    private static final String PATH = "/api/v1/auth/login";
    private static final String TRACE_ID = "trace-123";
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Test
    void invalidCredentials_mapsToBadCredentials401() {
        SentinelResponse response = SentinelRuleHandlers.ruleInvalidCredentials(
                new InvalidCredentialsException(),
                PATH,
                TRACE_ID
        );

        assertResponse(response, 401, SentinelErrorCode.BAD_CREDENTIALS, "Invalid credentials");
    }

    @Test
    void accountLocked_mapsToForbidden403() {
        SentinelResponse response = SentinelRuleHandlers.ruleAccountLocked(
                new AccountLockedException(),
                PATH,
                TRACE_ID
        );

        assertResponse(response, 403, SentinelErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked");
    }

    @Test
    void userNotMemberOfSpace_mapsToForbidden403() {
        SentinelResponse response = SentinelRuleHandlers.ruleUserNotMemberOfSpace(
                new UserNotMemberOfSpaceException(SPACE_ID),
                PATH,
                TRACE_ID
        );

        assertResponse(response, 403, SentinelErrorCode.USER_NOT_MEMBER_OF_SPACE, "No local user in space " + SPACE_ID);
    }

    @Test
    void organizationNotFound_mapsToNotFound404() {
        SentinelResponse response = SentinelRuleHandlers.ruleOrganizationNotFound(
                new OrganizationNotFoundException("Organization not found: takibo-iam"),
                PATH,
                TRACE_ID
        );

        assertResponse(response, 404, SentinelErrorCode.ORGANIZATION_NOT_FOUND, "Organization not found: takibo-iam");
    }

    @Test
    void tmsSpaceNotFound_mapsToSpaceNotFound404() {
        SentinelResponse response = SentinelRuleHandlers.ruleTmsSpaceNotFound(
                new RuntimeException("Space not found"),
                PATH,
                TRACE_ID
        );

        assertResponse(response, 404, SentinelErrorCode.SPACE_NOT_FOUND, "Space not found");
    }

    @Test
    void userNotActive_mapsToForbidden403() {
        SentinelResponse response = SentinelRuleHandlers.ruleUserNotActive(
                new UserNotActiveException(USER_ID, UserStatus.SUSPENDED),
                PATH,
                TRACE_ID
        );

        assertResponse(response, 403, SentinelErrorCode.USER_NOT_ACTIVE,
                "User " + USER_ID + " is not active (status: SUSPENDED)");
    }

    private void assertResponse(SentinelResponse response, int status, SentinelErrorCode code, String message) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.code()).isEqualTo(code.name());
        assertThat(response.message()).isEqualTo(message);
        assertThat(response.path()).isEqualTo(PATH);
        assertThat(response.traceId()).isEqualTo(TRACE_ID);
    }
}
