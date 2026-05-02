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

    static SentinelResponse ruleInvalidStatusTransition(InvalidStatusTransitionException ex, String path, String traceId) {
        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.INVALID_STATUS_TRANSITION, ex.getMessage(), path, traceId);
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

