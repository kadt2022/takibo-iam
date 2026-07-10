package com.takibo.securitymanagement.sentinel.rule;

import com.takibo.identitycore.domain.exception.*;
import com.takibo.securitymanagement.domain.exception.InvalidTokenException;
import com.takibo.securitymanagement.sentinel.advice.SentinelErrorCode;
import com.takibo.securitymanagement.sentinel.advice.SentinelResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.Objects;

public final class SentinelRuleHandlers {

    static final String REQUEST_INVALID = "Requête invalide.";
    private static final String MSG_BAD_CREDENTIALS = "Identifiants incorrects.";
    private static final String MSG_AUTH_REQUIRED = "Authentification requise.";
    private static final String MSG_ACCESS_DENIED = "Accès refusé.";

    private SentinelRuleHandlers() {}

    public static SentinelRule<Throwable> genericRule() {
        return (ex, path, trace) -> response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                SentinelErrorCode.INTERNAL_ERROR,
                "Une erreur inattendue est survenue.",
                path,
                trace
        );
    }

    static SentinelResponse ruleMethodArgumentNotValid(MethodArgumentNotValidException ex, String path, String traceId) {
        String msg = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(REQUEST_INVALID);

        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
    }

    static SentinelResponse ruleConstraintViolation(ConstraintViolationException ex, String path, String traceId) {
        String msg = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(REQUEST_INVALID);

        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
    }

    static SentinelResponse ruleBind(BindException ex, String path, String traceId) {
        String msg = ex.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(REQUEST_INVALID);

        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
    }

    static SentinelResponse ruleNotReadable(HttpMessageNotReadableException ex, String path, String traceId) {
        String msg = "Le format du payload est invalide ou des champs requis sont manquants.";
        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
    }

    static SentinelResponse ruleIllegalArgument(IllegalArgumentException ex, String path, String traceId) {
        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Paramètre invalide."
                : ex.getMessage();

        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
    }

    static SentinelResponse ruleClientProfileInvalid(Throwable ex, String path, String traceId) {
        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Configuration de client OAuth2 invalide."
                : ex.getMessage();

        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.OAUTH_CLIENT_PROFILE_INVALID, msg, path, traceId);
    }

    static SentinelResponse ruleUserNotFound(UserNotFoundException ex, String path, String traceId) {
        return response(HttpStatus.NOT_FOUND, SentinelErrorCode.USER_NOT_FOUND, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleUserAlreadyExists(UserAlreadyExistsException ex, String path, String traceId) {
        return response(HttpStatus.CONFLICT, SentinelErrorCode.USER_ALREADY_EXISTS, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleEmailAlreadyExists(EmailAlreadyExistsException ex, String path, String traceId) {
        return response(HttpStatus.CONFLICT, SentinelErrorCode.EMAIL_ALREADY_EXISTS, ex.getMessage(), path, traceId);
    }

    static SentinelResponse rulePasswordPolicyViolation(PasswordPolicyViolationException ex, String path, String traceId) {
        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.PASSWORD_POLICY_VIOLATION, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleMultipleUsersMatched(MultipleUsersMatchedException ex, String path, String traceId) {
        return response(HttpStatus.CONFLICT, SentinelErrorCode.NON_UNIQUE_RESULT, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleUserCreation(UserCreationException ex, String path, String traceId) {
        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.USER_CREATION_ERROR, ex.getMessage(), path, traceId);
    }

    // 409 : une transition invalide est un conflit avec l'état courant de la ressource,
    // pas une requête malformée (décision PR #24).
    static SentinelResponse ruleInvalidStatusTransition(InvalidStatusTransitionException ex, String path, String traceId) {
        return response(HttpStatus.CONFLICT, SentinelErrorCode.INVALID_STATUS_TRANSITION, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleSpaceGuard(SpaceGuardException ex, String path, String traceId) {
        SentinelErrorCode mapped = switch (ex.getCode()) {
            case SPACE_DISABLED -> SentinelErrorCode.SPACE_DISABLED;
            case SPACE_SUSPENDED -> SentinelErrorCode.SPACE_SUSPENDED;
            default -> SentinelErrorCode.SPACE_STATUS_UNKNOWN;
        };

        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? defaultMessage(mapped)
                : ex.getMessage();

        return response(HttpStatus.FORBIDDEN, mapped, message, path, traceId);
    }

    static SentinelResponse ruleSpaceNotActive(SpaceNotActiveException ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.SPACE_NOT_ACTIVE, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleSpaceNotFound(SpaceNotFoundException ex, String path, String traceId) {
        return response(HttpStatus.NOT_FOUND, SentinelErrorCode.SPACE_NOT_FOUND, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleInvalidToken(InvalidTokenException ex, String path, String traceId) {
        return response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.INVALID_TOKEN, ex.getMessage(), path, traceId);
    }

    /**
     * Message volontairement générique et identique pour toutes les causes
     * (email inconnu, mauvais password, credentials absents, account d'une autre org) :
     * zéro oracle d'énumération. La cause réelle vit dans les logs/audit, pas dans la réponse.
     */
    static SentinelResponse ruleInvalidCredentials(InvalidCredentialsException ex, String path, String traceId) {
        return response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.BAD_CREDENTIALS, "Invalid credentials", path, traceId);
    }

    static SentinelResponse ruleAccountLocked(AccountLockedException ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked", path, traceId);
    }

    static SentinelResponse ruleUserNotMemberOfSpace(UserNotMemberOfSpaceException ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.USER_NOT_MEMBER_OF_SPACE, ex.getMessage(), path, traceId);
    }

    // Password déjà prouvé : révéler l'état local n'est plus un oracle. Le statut
    // du user est une frontière d'accès -> 403, pas 401.
    static SentinelResponse ruleUserNotActive(UserNotActiveException ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.USER_NOT_ACTIVE, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleOrganizationNotFound(OrganizationNotFoundException ex, String path, String traceId) {
        return response(HttpStatus.NOT_FOUND, SentinelErrorCode.ORGANIZATION_NOT_FOUND, ex.getMessage(), path, traceId);
    }

    // Catalogue RBAC : un code hors frontière (rôle plateforme compris) N'EXISTE PAS -> 404.
    static SentinelResponse ruleRoleNotFound(RoleNotFoundException ex, String path, String traceId) {
        return response(HttpStatus.NOT_FOUND, SentinelErrorCode.ROLE_NOT_FOUND, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleGroupNotFound(GroupNotFoundException ex, String path, String traceId) {
        return response(HttpStatus.NOT_FOUND, SentinelErrorCode.GROUP_NOT_FOUND, ex.getMessage(), path, traceId);
    }

    static SentinelResponse rulePermissionNotFound(PermissionNotFoundException ex, String path, String traceId) {
        return response(HttpStatus.NOT_FOUND, SentinelErrorCode.PERMISSION_NOT_FOUND, ex.getMessage(), path, traceId);
    }

    // Gouvernance des assignations : la nature interdite est une politique (403),
    // le dernier chemin admin et la self-demotion sont des conflits d'état (409).
    static SentinelResponse ruleRoleTypeNotAllowed(RoleTypeNotAllowedException ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.ROLE_TYPE_NOT_ALLOWED, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleGroupTypeNotAllowed(GroupTypeNotAllowedException ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.GROUP_TYPE_NOT_ALLOWED, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleRoleScopeEscalation(RoleScopeEscalationException ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.ROLE_SCOPE_ESCALATION_DENIED,
                ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleLastAdminRemoval(LastAdminRemovalException ex, String path, String traceId) {
        return response(HttpStatus.CONFLICT, SentinelErrorCode.LAST_SPACE_ADMIN_REMOVAL_DENIED,
                ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleSelfDemotion(SelfDemotionException ex, String path, String traceId) {
        return response(HttpStatus.CONFLICT, SentinelErrorCode.SELF_DEMOTION_DENIED, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleBadCredentials(Throwable ex, String path, String traceId) {
        return response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.BAD_CREDENTIALS, MSG_BAD_CREDENTIALS, path, traceId);
    }

    static SentinelResponse ruleAuthenticationFailed(Throwable ex, String path, String traceId) {
        return response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.AUTHENTICATION_FAILED, MSG_AUTH_REQUIRED, path, traceId);
    }

    static SentinelResponse ruleAccessDenied(Throwable ex, String path, String traceId) {
        return response(HttpStatus.FORBIDDEN, SentinelErrorCode.ACCESS_DENIED, MSG_ACCESS_DENIED, path, traceId);
    }

    // ===== OAUTH2 / MANAGEMENT ERRORS =====

    static SentinelResponse ruleClientAlreadyExists(Throwable ex, String path, String traceId) {
        return response(HttpStatus.CONFLICT, SentinelErrorCode.OAUTH_CLIENT_ALREADY_EXISTS, ex.getMessage(), path, traceId);
    }

    // Exception TMS (soft dependency) : même contrat 404 que le SpaceNotFound de
    // TIS-CORE, message générique — jamais d'oracle sur l'existence dans une autre org.
    static SentinelResponse ruleTmsSpaceNotFound(Throwable ex, String path, String traceId) {
        return response(HttpStatus.NOT_FOUND, SentinelErrorCode.SPACE_NOT_FOUND, ex.getMessage(), path, traceId);
    }

    // ===== OAUTH2 / AUTHORIZATION SERVER ERRORS =====

    static SentinelResponse ruleOAuth2InvalidRequest(Throwable ex, String path, String traceId) {
        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.OAUTH2_INVALID_REQUEST, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleOAuth2InvalidClient(Throwable ex, String path, String traceId) {
        return response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.OAUTH2_INVALID_CLIENT, ex.getMessage(), path, traceId);
    }

    static SentinelResponse ruleTenantResolutionFailed(Throwable ex, String path, String traceId) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, SentinelErrorCode.TENANT_RESOLUTION_FAILED, ex.getMessage(), path, traceId);
    }

    private static SentinelResponse response(HttpStatus status, SentinelErrorCode code, String message, String path, String traceId) {
        return new SentinelResponse(Instant.now(), status.value(), code.name(), message, path, traceId);
    }

    private static String defaultMessage(SentinelErrorCode code) {
        return switch (code) {
            case SPACE_DISABLED -> "Space is DISABLED";
            case SPACE_SUSPENDED -> "Space is SUSPENDED";
            case SPACE_NOT_ACTIVE -> "Space is NOT ACTIVE";
            case SPACE_NOT_FOUND -> "Space not found";
            case SPACE_STATUS_UNKNOWN -> "Space status unknown";
            default -> "Unexpected error";
        };
    }
}

