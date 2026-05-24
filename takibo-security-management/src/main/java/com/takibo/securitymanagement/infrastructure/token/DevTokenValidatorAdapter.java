package com.takibo.securitymanagement.infrastructure.token;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "takibo.security.token.validator", havingValue = "dev")
public class DevTokenValidatorAdapter implements TokenValidatorAdapter {

    private static final String DEFAULT_ORG_ID = "fa9c5979-5e8e-489a-8614-52b1d1578bbe";
    private static final String DEFAULT_SPACE_ID = "00bab121-b495-4e67-a836-5a87636bab76";
    private static final String DEFAULT_USER_ID = "8799a2a1-707f-4dc0-b1ec-d8a766986769";
    private static final String DEFAULT_ACCOUNT_ID = "d03675f5-b38c-4eae-898e-29c4ff09a5c1";

    private static final String PLATFORM_USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PLATFORM_ACCOUNT_ID = "22222222-2222-2222-2222-222222222222";

    @Override
    public Map<String, Object> validate(String rawToken) throws JwtValidationException {
        final String token = Optional.ofNullable(rawToken).orElse("").trim();

        if (token.startsWith("dev-org-admin:")) {
            String[] parts = token.split(":", 3);
            if (parts.length < 2) {
                throw new JwtValidationException(
                        "Invalid dev-org-admin token format, expected dev-org-admin:<spaceId>[:<orgId>]"
                );
            }

            String spaceId = ensureUuid(parts[1], "spaceId");
            String orgId = (parts.length >= 3) ? ensureUuid(parts[2], "orgId") : DEFAULT_ORG_ID;

            return std(
                    "user-org-admin-1",
                    "user-org-admin-1",
                    List.of("ORG_ADMIN"),
                    List.of("USER_DELETE", "USER_READ", "SECRET_READ"),
                    orgId,
                    spaceId,
                    DEFAULT_USER_ID,
                    DEFAULT_ACCOUNT_ID
            );
        }

        return switch (token) {
            case "dev-org-admin" -> std(
                    "user-org-admin-1",
                    "user-org-admin-1",
                    List.of("ORG_ADMIN"),
                    List.of("USER_DELETE", "USER_READ", "SECRET_READ"),
                    DEFAULT_ORG_ID,
                    DEFAULT_SPACE_ID,
                    DEFAULT_USER_ID,
                    DEFAULT_ACCOUNT_ID
            );

            case "dev-platform-admin" -> std(
                    "user-platform-admin",
                    "user-platform-admin",
                    List.of("PLATFORM_ADMIN"),
                    List.of(),
                    null,
                    null,
                    PLATFORM_USER_ID,
                    PLATFORM_ACCOUNT_ID
            );

            default -> throw new JwtValidationException("Unknown dev token: " + token);
        };
    }

    private static Map<String, Object> std(
            String sub,
            String username,
            List<String> roles,
            List<String> permissions,
            String orgId,
            String spaceId,
            String userId,
            String accountId
    ) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("preferred_username", username);
        claims.put("username", username);

        if (userId != null) {
            claims.put("userId", userId);
        }
        if (accountId != null) {
            claims.put("accountId", accountId);
        }

        claims.put("roles", roles);
        claims.put("permissions", permissions);

        if (orgId != null) {
            claims.put("orgId", orgId);
        }
        if (spaceId != null) {
            claims.put("spaceId", spaceId);
        }

        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(3600).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());

        return Map.copyOf(claims);
    }

    private static String ensureUuid(String value, String field) throws JwtValidationException {
        try {
            UUID.fromString(value);
            return value;
        } catch (Exception e) {
            throw new JwtValidationException("Invalid " + field + " UUID: " + value);
        }
    }
}
