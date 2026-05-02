package com.takibo.managementservice.application.security;

import com.takibo.managementservice.application.port.CurrentActorProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentActorProviderImpl implements CurrentActorProvider {

    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return SYSTEM_ACTOR_ID;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof TakiboPrincipal tp) {
            return tp.userId() != null ? tp.userId() : SYSTEM_ACTOR_ID;
        }

        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            Object userId = jwt.getClaims().get("user_id");
            if (userId == null) userId = jwt.getClaims().get("userId");
            if (userId == null) userId = jwt.getSubject();

            try {
                return UUID.fromString(String.valueOf(userId));
            } catch (Exception ignore) {
                return SYSTEM_ACTOR_ID;
            }
        }

        if (principal instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (Exception ignore) {
                return SYSTEM_ACTOR_ID;
            }
        }

        return SYSTEM_ACTOR_ID;
    }


    @Override
    public ActorSource source() {
        return ActorSource.SYSTEM;
    }

    private UUID tryUuidFromJwt(Jwt jwt) {
        if (jwt == null) return null;

        UUID id;

        id = tryUuid(jwt.getClaimAsString("userId"), null);
        if (id != null) return id;

        id = tryUuid(jwt.getClaimAsString("user_id"), null);
        if (id != null) return id;

        id = tryUuid(jwt.getClaimAsString("uid"), null);
        if (id != null) return id;

        id = tryUuid(jwt.getClaimAsString("sub"), null);
        if (id != null) return id;

        return null;
    }

    private UUID tryUuid(String value, UUID fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return UUID.fromString(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
