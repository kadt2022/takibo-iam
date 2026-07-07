package com.takibo.securitymanagement.sentinel.rule;

import com.takibo.identitycore.domain.exception.AccountLockedException;
import com.takibo.identitycore.domain.exception.GroupNotFoundException;
import com.takibo.identitycore.domain.exception.GroupTypeNotAllowedException;
import com.takibo.identitycore.domain.exception.InvalidCredentialsException;
import com.takibo.identitycore.domain.exception.LastAdminRemovalException;
import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.PermissionNotFoundException;
import com.takibo.identitycore.domain.exception.RoleNotFoundException;
import com.takibo.identitycore.domain.exception.RoleScopeEscalationException;
import com.takibo.identitycore.domain.exception.RoleTypeNotAllowedException;
import com.takibo.identitycore.domain.exception.SelfDemotionException;
import com.takibo.identitycore.domain.exception.UserNotActiveException;
import com.takibo.identitycore.domain.exception.UserNotMemberOfSpaceException;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.securitymanagement.sentinel.advice.SentinelErrorCode;
import com.takibo.securitymanagement.sentinel.advice.SentinelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelRuleRegistrarTest {

    private static final String PATH = "/api/v1/auth/login";
    private static final String TRACE_ID = "trace-123";
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private final SentinelRuleRegistry registry = registeredRegistry();

    @Test
    void registerDefaults_resolvesHumanLoginRules() {
        assertResolved(new InvalidCredentialsException(), 401, SentinelErrorCode.BAD_CREDENTIALS);
        assertResolved(new AccountLockedException(), 403, SentinelErrorCode.ACCOUNT_LOCKED);
        assertResolved(new UserNotMemberOfSpaceException(SPACE_ID), 403, SentinelErrorCode.USER_NOT_MEMBER_OF_SPACE);
        assertResolved(new UserNotActiveException(USER_ID, UserStatus.SUSPENDED), 403, SentinelErrorCode.USER_NOT_ACTIVE);
        assertResolved(new OrganizationNotFoundException("Organization not found: takibo-iam"), 404, SentinelErrorCode.ORGANIZATION_NOT_FOUND);
    }

    @Test
    void registerDefaults_resolvesRbacCatalogRules() {
        assertResolved(new RoleNotFoundException("Role not found in this space: NOPE"),
                404, SentinelErrorCode.ROLE_NOT_FOUND);
        assertResolved(new GroupNotFoundException("Group not found in this space: NOPE"),
                404, SentinelErrorCode.GROUP_NOT_FOUND);
        assertResolved(new PermissionNotFoundException("Permission not found in this space: NOPE"),
                404, SentinelErrorCode.PERMISSION_NOT_FOUND);
    }

    @Test
    void registerDefaults_resolvesRbacGovernanceRules() {
        assertResolved(new RoleTypeNotAllowedException("Business role not assignable"),
                403, SentinelErrorCode.ROLE_TYPE_NOT_ALLOWED);
        assertResolved(new GroupTypeNotAllowedException("Business group memberships not allowed"),
                403, SentinelErrorCode.GROUP_TYPE_NOT_ALLOWED);
        assertResolved(new RoleScopeEscalationException("Organization-level authority required"),
                403, SentinelErrorCode.ROLE_SCOPE_ESCALATION_DENIED);
        assertResolved(new LastAdminRemovalException("Cannot remove the last R_SPACE_ADMIN"),
                409, SentinelErrorCode.LAST_SPACE_ADMIN_REMOVAL_DENIED);
        assertResolved(new SelfDemotionException("Cannot self-remove admin role"),
                409, SentinelErrorCode.SELF_DEMOTION_DENIED);
    }

    @Test
    void registerDefaults_resolvesSpringSecuritySoftDependency() {
        assertResolved(new AccessDeniedException("denied"), 403, SentinelErrorCode.ACCESS_DENIED);
    }

    private void assertResolved(Throwable throwable, int status, SentinelErrorCode code) {
        SentinelResponse response = registry.resolve(throwable).toResponse(throwable, PATH, TRACE_ID);

        assertThat(response.status()).isEqualTo(status);
        assertThat(response.code()).isEqualTo(code.name());
        assertThat(response.path()).isEqualTo(PATH);
        assertThat(response.traceId()).isEqualTo(TRACE_ID);
    }

    private SentinelRuleRegistry registeredRegistry() {
        SentinelRuleRegistry registry = new SentinelRuleRegistry(SentinelRuleHandlers.genericRule());
        SentinelRuleRegistrar.registerDefaults(registry);
        return registry;
    }
}
