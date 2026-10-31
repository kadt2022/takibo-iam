package com.takibo.securitymanagement.sentinel.rule;

import com.takibo.identitycore.domain.exception.*;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckUnavailableException;
import com.takibo.securitymanagement.domain.exception.InvalidTokenException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

public final class SentinelRuleRegistrar {

    private SentinelRuleRegistrar() {}

    public static void registerDefaults(SentinelRuleRegistry registry) {
        registry.register(MethodArgumentNotValidException.class, SentinelRuleHandlers::ruleMethodArgumentNotValid);
        registry.register(ConstraintViolationException.class, SentinelRuleHandlers::ruleConstraintViolation);
        registry.register(BindException.class, SentinelRuleHandlers::ruleBind);
        registry.register(HttpMessageNotReadableException.class, SentinelRuleHandlers::ruleNotReadable);

        // Socle web : requête autorisée que Spring MVC ne peut pas servir. Statut explicite,
        // message générique : jamais de 500, jamais de nom de type ni de message de conversion.
        registry.register(HttpRequestMethodNotSupportedException.class, SentinelRuleHandlers.ruleMethodNotAllowed());
        registry.register(HttpMediaTypeNotSupportedException.class, SentinelRuleHandlers.ruleUnsupportedMediaType());
        registry.register(HttpMediaTypeNotAcceptableException.class, SentinelRuleHandlers.ruleNotAcceptable());
        registry.register(MethodArgumentTypeMismatchException.class, SentinelRuleHandlers.ruleInvalidRequestParameter());
        registry.register(MissingServletRequestParameterException.class, SentinelRuleHandlers.ruleInvalidRequestParameter());
        registry.register(MissingRequestHeaderException.class, SentinelRuleHandlers.ruleInvalidRequestParameter());

        registry.register(IllegalArgumentException.class, SentinelRuleHandlers::ruleIllegalArgument);
        registry.tryRegister("com.takibo.managementservice.domain.exception.InvalidClientConfigurationException",
                SentinelRuleHandlers::ruleClientProfileInvalid);
        registry.tryRegister("com.takibo.managementservice.domain.exception.ClientAlreadyExistsException",
                SentinelRuleHandlers::ruleClientAlreadyExists);
        registry.tryRegister("com.takibo.managementservice.domain.exception.OAuthClientSecretRotationConflictException",
                SentinelRuleHandlers::ruleOAuthClientSecretRotationConflict);
        registry.tryRegister("com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException",
                SentinelRuleHandlers::ruleOrganizationAlreadyExists);
        registry.tryRegister("com.takibo.managementservice.domain.exception.SpaceCodeAlreadyExistsException",
                SentinelRuleHandlers::ruleSpaceAlreadyExists);
        registry.tryRegister("com.takibo.managementservice.domain.exception.SpaceNotFoundException",
                SentinelRuleHandlers::ruleTmsSpaceNotFound);

        registry.register(UserNotFoundException.class, SentinelRuleHandlers::ruleUserNotFound);
        registry.register(UserAlreadyExistsException.class, SentinelRuleHandlers::ruleUserAlreadyExists);
        registry.register(EmailAlreadyExistsException.class, SentinelRuleHandlers::ruleEmailAlreadyExists);
        registry.register(PasswordPolicyViolationException.class, SentinelRuleHandlers::rulePasswordPolicyViolation);
        registry.register(MultipleUsersMatchedException.class, SentinelRuleHandlers::ruleMultipleUsersMatched);
        registry.register(UserCreationException.class, SentinelRuleHandlers::ruleUserCreation);
        registry.register(InvalidStatusTransitionException.class, SentinelRuleHandlers::ruleInvalidStatusTransition);

        // Catalogue RBAC (read-side)
        registry.register(RoleNotFoundException.class, SentinelRuleHandlers::ruleRoleNotFound);
        registry.register(GroupNotFoundException.class, SentinelRuleHandlers::ruleGroupNotFound);
        registry.register(PermissionNotFoundException.class, SentinelRuleHandlers::rulePermissionNotFound);

        // Gouvernance RBAC (assignations)
        registry.register(RoleTypeNotAllowedException.class, SentinelRuleHandlers::ruleRoleTypeNotAllowed);
        registry.register(GroupTypeNotAllowedException.class, SentinelRuleHandlers::ruleGroupTypeNotAllowed);
        registry.register(RoleScopeEscalationException.class, SentinelRuleHandlers::ruleRoleScopeEscalation);
        registry.register(LastAdminRemovalException.class, SentinelRuleHandlers::ruleLastAdminRemoval);
        registry.register(SelfDemotionException.class, SentinelRuleHandlers::ruleSelfDemotion);

        registry.register(SpaceGuardException.class, SentinelRuleHandlers::ruleSpaceGuard);
        registry.register(SpaceNotActiveException.class, SentinelRuleHandlers::ruleSpaceNotActive);
        registry.register(SpaceNotFoundException.class, SentinelRuleHandlers::ruleSpaceNotFound);
        registry.register(OrganizationNotFoundException.class, SentinelRuleHandlers::ruleOrganizationNotFound);

        // Login humain — IAM 31 : la surface d'authentification ne raconte rien,
        // toutes les causes convergent vers un 401 uniforme.
        registry.register(AuthenticationFailedException.class, SentinelRuleHandlers::ruleLoginAuthenticationFailed);
        registry.register(InvalidCredentialsException.class, SentinelRuleHandlers::ruleInvalidCredentials);
        registry.register(AccountLockedException.class, SentinelRuleHandlers::ruleAccountLocked);
        registry.register(UserNotMemberOfSpaceException.class, SentinelRuleHandlers::ruleUserNotMemberOfSpace);
        registry.register(UserNotActiveException.class, SentinelRuleHandlers::ruleUserNotActive);

        registry.register(InvalidTokenException.class, SentinelRuleHandlers::ruleInvalidToken);
        registry.register(BoundaryCheckUnavailableException.class, SentinelRuleHandlers::ruleBoundaryUnavailable);

        // OAuth2 / Authorization Server exceptions (soft dependency)
        registry.tryRegister("com.takibo.authorizationserver.domain.exception.TakiboInvalidRequestException",
                SentinelRuleHandlers::ruleOAuth2InvalidRequest);
        registry.tryRegister("com.takibo.authorizationserver.domain.exception.TakiboInvalidClientException",
                SentinelRuleHandlers::ruleOAuth2InvalidClient);
        registry.tryRegister("com.takibo.authorizationserver.domain.exception.TakiboServerErrorException",
                SentinelRuleHandlers.ruleTenantResolutionFailed());

        // Spring Security exceptions (soft dependency)
        registry.tryRegister("org.springframework.security.authentication.BadCredentialsException",
                SentinelRuleHandlers::ruleBadCredentials);
        registry.tryRegister("org.springframework.security.core.AuthenticationException",
                SentinelRuleHandlers::ruleAuthenticationFailed);
        registry.tryRegister("org.springframework.security.access.AccessDeniedException",
                SentinelRuleHandlers::ruleAccessDenied);
    }
}

