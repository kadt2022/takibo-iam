package com.takibo.managementservice.infrastructure.security;

import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.managementservice.application.port.CurrentActorProvider;
import com.takibo.managementservice.domain.model.ActorSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class CurrentActorProviderImpl implements CurrentActorProvider {

    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CurrentAccountContextCase currentAccountContext;

    public CurrentActorProviderImpl(CurrentAccountContextCase currentAccountContext) {
        this.currentAccountContext = currentAccountContext;
    }

    @Override
    public UUID currentUserId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .flatMap(this::resolveUserId)
                .orElse(SYSTEM_ACTOR_ID);
    }

    @Override
    public UUID currentAccountId() {
        return currentAccountContext.requireCurrentAccountId();
    }

    @Override
    public ActorSource source() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Aucun acteur situé : traitement interne / bootstrap. C'est le seul cas
        // légitime de SYSTEM — jamais un fallback silencieux d'un contexte invalide.
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ActorSource.SYSTEM;
        }

        // Un token humain porte un account (IAM 31) ; un token machine
        // (client_credentials) n'en porte pas et agit comme SERVICE_ACCOUNT.
        return carriesAccount(authentication.getPrincipal())
                ? ActorSource.HUMAN
                : ActorSource.SERVICE_ACCOUNT;
    }

    private boolean carriesAccount(Object principal) {
        if (principal instanceof TakiboPrincipal takiboPrincipal) {
            return takiboPrincipal.accountId() != null;
        }
        if (principal instanceof Jwt jwt) {
            return Stream.of("account_id", "accountId")
                    .map(jwt.getClaims()::get)
                    .anyMatch(Objects::nonNull);
        }
        return false;
    }

    private Optional<UUID> resolveUserId(Object principal) {
        if (principal instanceof TakiboPrincipal takiboPrincipal) {
            return Optional.ofNullable(takiboPrincipal.userId());
        }
        if (principal instanceof Jwt jwt) {
            return Stream.of(
                            jwt.getClaims().get("user_id"),
                            jwt.getClaims().get("userId"),
                            jwt.getSubject())
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(CurrentActorProviderImpl::parseUuid)
                    .flatMap(Optional::stream)
                    .findFirst();
        }
        if (principal instanceof String value) {
            return parseUuid(value);
        }
        return Optional.empty();
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
