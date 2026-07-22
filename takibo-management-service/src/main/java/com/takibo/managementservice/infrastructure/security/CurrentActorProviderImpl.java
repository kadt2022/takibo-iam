package com.takibo.managementservice.infrastructure.security;

import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.managementservice.application.port.CurrentActorProvider;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class CurrentActorProviderImpl implements CurrentActorProvider {

    private final CurrentAccountContextCase currentAccountContext;

    public CurrentActorProviderImpl(CurrentAccountContextCase currentAccountContext) {
        this.currentAccountContext = currentAccountContext;
    }

    @Override
    public UUID currentUserId() {
        Authentication authentication = requireAuthenticatedActor();
        if (source(authentication) != ActorSource.HUMAN) {
            throw invalidActor("Current actor is not a human user");
        }

        if (authentication instanceof TakiboSecurityContextCarrier carrier) {
            return carrier.getSecurityContext().attributes()
                    .get(StandardAttributeKeys.USER_ID, UUID.class)
                    .or(() -> parseUuid(carrier.getSecurityContext().subject().subjectId()))
                    .orElseThrow(() -> invalidActor(
                            "Authenticated human actor does not expose a valid user_id"));
        }

        return resolveUserId(authentication.getPrincipal())
                .orElseThrow(() -> invalidActor(
                        "Authenticated human actor does not expose a valid user_id"));
    }

    @Override
    public UUID currentAccountId() {
        requireAuthenticatedActor();
        return currentAccountContext.requireCurrentAccountId();
    }

    @Override
    public ActorSource source() {
        return source(requireAuthenticatedActor());
    }

    private ActorSource source(Authentication authentication) {
        if (authentication instanceof TakiboSecurityContextCarrier carrier) {
            SubjectNature nature = carrier.getSecurityContext().subject().nature();
            return switch (nature) {
                case HUMAN -> ActorSource.HUMAN;
                case SERVICE -> ActorSource.SERVICE_ACCOUNT;
                case SYSTEM -> throw invalidActor(
                        "SYSTEM actor is not allowed through an authenticated request");
            };
        }

        return resolveLegacySource(authentication.getPrincipal());
    }

    private ActorSource resolveLegacySource(Object principal) {
        if (principal instanceof Jwt jwt) {
            Optional<String> subjectType = firstStringClaim(jwt, "subject_type", "subjectType")
                    .map(value -> value.toUpperCase(Locale.ROOT));

            if (subjectType.filter("HUMAN"::equals).isPresent()) {
                return ActorSource.HUMAN;
            }
            if (subjectType.filter(CurrentActorProviderImpl::isServiceSubjectType).isPresent()) {
                return ActorSource.SERVICE_ACCOUNT;
            }
            if (subjectType.isPresent()) {
                throw invalidActor("Unsupported legacy subject_type: " + subjectType.get());
            }

            // Transition only: old human tokens carried both identity claims; old
            // client_credentials tokens carried a client_id. Anything else is ambiguous.
            if (resolveUserId(jwt).isPresent() && resolveAccountId(jwt).isPresent()) {
                return ActorSource.HUMAN;
            }
            if (firstStringClaim(jwt, "client_id", "clientId").isPresent()) {
                return ActorSource.SERVICE_ACCOUNT;
            }
        }

        if (principal instanceof TakiboPrincipal takiboPrincipal
                && takiboPrincipal.userId() != null
                && takiboPrincipal.accountId() != null) {
            return ActorSource.HUMAN;
        }

        throw invalidActor("Unable to determine authenticated actor source");
    }

    private Authentication requireAuthenticatedActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw invalidActor("No authenticated actor");
        }
        return authentication;
    }

    private Optional<UUID> resolveUserId(Object principal) {
        if (principal instanceof TakiboPrincipal takiboPrincipal) {
            return Optional.ofNullable(takiboPrincipal.userId());
        }
        if (principal instanceof Jwt jwt) {
            return firstUuidClaim(jwt, "user_id", "userId");
        }
        return Optional.empty();
    }

    private Optional<UUID> resolveAccountId(Jwt jwt) {
        return firstUuidClaim(jwt, "account_id", "accountId");
    }

    private static Optional<UUID> firstUuidClaim(Jwt jwt, String... names) {
        return Stream.of(names)
                .map(jwt.getClaims()::get)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(CurrentActorProviderImpl::parseUuid)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> firstStringClaim(Jwt jwt, String... names) {
        return Stream.of(names)
                .map(jwt.getClaims()::get)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst();
    }

    private static boolean isServiceSubjectType(String value) {
        return "CLIENT_APP".equals(value)
                || "SERVICE".equals(value)
                || "SERVICE_ACCOUNT".equals(value);
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static IllegalStateException invalidActor(String message) {
        return new IllegalStateException(message);
    }
}
