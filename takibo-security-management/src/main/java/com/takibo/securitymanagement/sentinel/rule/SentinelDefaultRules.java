//package com.takibo.securitymanagement.sentinel.rule;
//
//import com.takibo.identitycore.domain.exception.*;
//import com.takibo.securitymanagement.domain.exception.InvalidTokenException;
//import com.takibo.securitymanagement.sentinel.advice.SentinelErrorCode;
//import com.takibo.securitymanagement.sentinel.advice.SentinelResponse;
//import jakarta.validation.ConstraintViolation;
//import jakarta.validation.ConstraintViolationException;
//import org.springframework.context.support.DefaultMessageSourceResolvable;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.converter.HttpMessageNotReadableException;
//import org.springframework.validation.BindException;
//import org.springframework.web.bind.MethodArgumentNotValidException;
//
//import java.time.Instant;
//import java.util.Objects;
//
//public final class SentinelDefaultRules {
//
//    public static final String REQUEST_INVALID = "Requête invalide.";
//
//    private SentinelDefaultRules() {}
//
//    public static void registerDefaults(SentinelRuleRegistry registry) {
//        registry.register(MethodArgumentNotValidException.class, SentinelDefaultRules::ruleMethodArgumentNotValid);
//        registry.register(ConstraintViolationException.class, SentinelDefaultRules::ruleConstraintViolation);
//        registry.register(BindException.class, SentinelDefaultRules::ruleBind);
//        registry.register(HttpMessageNotReadableException.class, SentinelDefaultRules::ruleNotReadable);
//
//        registry.register(IllegalArgumentException.class, SentinelDefaultRules::ruleIllegalArgument);
//
//        registry.register(UserNotFoundException.class, (ex, path, trace) ->
//                response(HttpStatus.NOT_FOUND, SentinelErrorCode.USER_NOT_FOUND, ex.getMessage(), path, trace));
//        registry.register(UserAlreadyExistsException.class, (ex, path, trace) ->
//                response(HttpStatus.CONFLICT, SentinelErrorCode.USER_ALREADY_EXISTS, ex.getMessage(), path, trace));
//        registry.register(EmailAlreadyExistsException.class, (ex, path, trace) ->
//                response(HttpStatus.CONFLICT, SentinelErrorCode.EMAIL_ALREADY_EXISTS, ex.getMessage(), path, trace));
//        registry.register(PasswordPolicyViolationException.class, (ex, path, trace) ->
//                response(HttpStatus.BAD_REQUEST, SentinelErrorCode.PASSWORD_POLICY_VIOLATION, ex.getMessage(), path, trace));
//        registry.register(MultipleUsersMatchedException.class, (ex, path, trace) ->
//                response(HttpStatus.CONFLICT, SentinelErrorCode.NON_UNIQUE_RESULT, ex.getMessage(), path, trace));
//        registry.register(UserCreationException.class, (ex, path, trace) ->
//                response(HttpStatus.BAD_REQUEST, SentinelErrorCode.USER_CREATION_ERROR, ex.getMessage(), path, trace));
//        registry.tryRegister("org.springframework.security.access.AccessDeniedException",
//                (ex, path, trace) -> response(HttpStatus.FORBIDDEN, SentinelErrorCode.ACCESS_DENIED, "Accès refusé.", path, trace));
//        registry.tryRegister("org.springframework.security.authentication.BadCredentialsException",
//                (ex, path, trace) -> response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.BAD_CREDENTIALS, "Identifiants incorrects.", path, trace));
//        registry.register(InvalidStatusTransitionException.class, (ex, path, trace) ->
//                response(HttpStatus.BAD_REQUEST, SentinelErrorCode.INVALID_STATUS_TRANSITION,
//                        ex.getMessage(), path, trace));
//
//        registry.register(SpaceGuardException.class, SentinelDefaultRules::ruleSpaceGuard);
//
//        registry.register(SpaceNotActiveException.class,
//                (ex, path, trace) -> response(
//                        HttpStatus.FORBIDDEN,
//                        SentinelErrorCode.SPACE_NOT_ACTIVE,
//                        ex.getMessage(),
//                        path,
//                        trace
//                )
//        );
//
//        registry.register(SpaceNotFoundException.class,
//                (ex, path, trace) -> response(
//                        HttpStatus.NOT_FOUND,
//                        SentinelErrorCode.SPACE_NOT_FOUND,
//                        ex.getMessage(),
//                        path,
//                        trace
//                )
//        );
//
//// ═══════════════════════════════════════════════════
//        // SÉCURITÉ (401/403)
//        // ═══════════════════════════════════════════════════
//
//        // 401 - Token invalide (notre exception custom)
//        registry.register(InvalidTokenException.class, (ex, path, trace) ->
//                response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.INVALID_TOKEN,
//                        ex.getMessage(), path, trace));
//
//        // 401 - BadCredentials (Spring Security)
//        registry.tryRegister("org.springframework.security.authentication.BadCredentialsException",
//                (ex, path, trace) -> response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.BAD_CREDENTIALS,
//                        "Identifiants incorrects.", path, trace));
//
//        // 401 - AuthenticationException (Spring Security - fallback)
//        registry.tryRegister("org.springframework.security.core.AuthenticationException",
//                (ex, path, trace) -> response(HttpStatus.UNAUTHORIZED, SentinelErrorCode.AUTHENTICATION_FAILED,
//                        "Authentification requise.", path, trace));
//
//        // 403 - AccessDenied (Spring Security)
//        registry.tryRegister("org.springframework.security.access.AccessDeniedException",
//                (ex, path, trace) -> response(HttpStatus.FORBIDDEN, SentinelErrorCode.ACCESS_DENIED,
//                        "Accès refusé.", path, trace));
//
//        // ═══════════════════════════════════════════════════
//    }
//
//
//
//    public static SentinelRule<Throwable> genericRule() {
//        return (ex, path, trace) -> response(
//                HttpStatus.INTERNAL_SERVER_ERROR,
//                SentinelErrorCode.INTERNAL_ERROR,
//                "Une erreur inattendue est survenue.",
//                path,
//                trace
//        );
//    }
//
//    private static SentinelResponse ruleMethodArgumentNotValid(MethodArgumentNotValidException ex, String path, String traceId) {
//        String msg = ex.getBindingResult().getAllErrors().stream()
//                .map(DefaultMessageSourceResolvable::getDefaultMessage)
//                .filter(Objects::nonNull).filter(s -> !s.isBlank())
//                .findFirst().orElse(REQUEST_INVALID);
//        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
//    }
//
//    private static SentinelResponse ruleConstraintViolation(ConstraintViolationException ex, String path, String traceId) {
//        String msg = ex.getConstraintViolations().stream()
//                .map(ConstraintViolation::getMessage)
//                .filter(Objects::nonNull).filter(s -> !s.isBlank())
//                .findFirst().orElse(REQUEST_INVALID);
//        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
//    }
//
//    private static SentinelResponse ruleBind(BindException ex, String path, String traceId) {
//        String msg = ex.getAllErrors().stream()
//                .map(DefaultMessageSourceResolvable::getDefaultMessage)
//                .filter(Objects::nonNull).filter(s -> !s.isBlank())
//                .findFirst().orElse(REQUEST_INVALID);
//        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
//    }
//
//    private static SentinelResponse ruleNotReadable(HttpMessageNotReadableException ex, String path, String traceId) {
//        String msg = "Le format du payload est invalide ou des champs requis sont manquants.";
//        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
//    }
//
//    private static SentinelResponse ruleIllegalArgument(IllegalArgumentException ex, String path, String traceId) {
//        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
//                ? "Paramètre invalide." : ex.getMessage();
//        return response(HttpStatus.BAD_REQUEST, SentinelErrorCode.BAD_REQUEST, msg, path, traceId);
//    }
//
//    private static SentinelResponse response(HttpStatus status, SentinelErrorCode code, String message, String path, String traceId) {
//        return new SentinelResponse(Instant.now(), status.value(), code.name(), message, path, traceId);
//    }
//
//    private static SentinelResponse ruleSpaceGuard(SpaceGuardException ex, String path, String traceId) {
//        SentinelErrorCode mapped = switch (ex.getCode()) {
//            case SPACE_DISABLED -> SentinelErrorCode.SPACE_DISABLED;
//            case SPACE_SUSPENDED -> SentinelErrorCode.SPACE_SUSPENDED;
//            default -> SentinelErrorCode.SPACE_STATUS_UNKNOWN;
//        };
//        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
//                ? defaultMessage(mapped)
//                : ex.getMessage();
//        return response(HttpStatus.FORBIDDEN, mapped, message, path, traceId);
//    }
//
//    private static String defaultMessage(SentinelErrorCode code) {
//        return switch (code) {
//            case SPACE_DISABLED       -> "Space is DISABLED";
//            case SPACE_SUSPENDED      -> "Space is SUSPENDED";
//            case SPACE_NOT_ACTIVE     -> "Space is NOT ACTIVE";
//            case SPACE_NOT_FOUND      -> "Space not found";          // NEW
//            case SPACE_STATUS_UNKNOWN -> "Space status unknown";
//            default                   -> "Unexpected error";
//        };
//    }
//}
//
