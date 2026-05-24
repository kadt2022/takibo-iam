package com.takibo.securitymanagement.infrastructure.token;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@ConditionalOnProperty(name = "takibo.security.token.validator", havingValue = "tas", matchIfMissing = true)
public class TasJwtTokenValidatorAdapter implements TokenValidatorAdapter {

    private final JwtDecoder jwtDecoder;

    public TasJwtTokenValidatorAdapter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Map<String, Object> validate(String rawToken) throws JwtValidationException {
        try {
            Jwt jwt = jwtDecoder.decode(rawToken);
            return mapClaims(jwt);
        } catch (JwtException e) {
            throw new JwtValidationException("Invalid TAS JWT: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> mapClaims(Jwt jwt) {
        Map<String, Object> claims = new LinkedHashMap<>();

        String sub = jwt.getSubject();
        if (sub != null) claims.put("sub", sub);

        String iss = jwt.getClaimAsString("iss");
        if (iss != null) claims.put("iss", iss);

        if (jwt.getIssuedAt() != null) claims.put("iat", jwt.getIssuedAt().getEpochSecond());
        if (jwt.getExpiresAt() != null) claims.put("exp", jwt.getExpiresAt().getEpochSecond());

        String jti = jwt.getId();
        if (jti != null) claims.put("jti", jti);

        // TAS claims: snake_case -> camelCase expected by TakiboSecurityContextFactory
        String orgId = jwt.getClaimAsString("org_id");
        if (orgId != null) claims.put("orgId", orgId);

        String spaceId = jwt.getClaimAsString("space_id");
        if (spaceId != null) claims.put("spaceId", spaceId);

        // client_id: present in some SAS JWT profiles, useful for future adapters
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId != null) claims.put("clientId", clientId);

        // scope: normalize scope/scp regardless of String or List<String> representation
        String scope = normalizeScope(jwt.getClaims().get("scope"));
        if (scope == null) {
            scope = normalizeScope(jwt.getClaims().get("scp"));
        }
        if (scope != null) claims.put("scope", scope);

        return Map.copyOf(claims);
    }

    private String normalizeScope(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        if (value instanceof Iterable<?> values) {
            String result = StreamSupport.stream(values.spliterator(), false)
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" "));
            return result.isBlank() ? null : result;
        }
        return null;
    }
}
