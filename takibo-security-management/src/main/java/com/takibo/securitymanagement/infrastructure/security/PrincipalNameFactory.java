package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.model.TakiboSecurityContext;

import java.util.Map;
import java.util.UUID;

final class PrincipalNameFactory {

    private PrincipalNameFactory() {
    }

    static String from(Map<String, Object> claims, TakiboSecurityContext ctx) {
        String preferred = ClaimReader.readString(claims, "preferred_username");
        if (preferred != null) return preferred;

        String username = ClaimReader.readString(claims, "username");
        if (username != null) return username;

        String upn = ClaimReader.readString(claims, "upn");
        if (upn != null) return upn;

        String actorId = ctx != null && ctx.subject() != null ? ctx.subject().subjectId() : null;
        if (actorId != null && !actorId.isBlank()) return actorId;

        UUID accountId = ClaimReader.readUuid(claims, "accountId");
        if (accountId != null) return accountId.toString();

        return "anonymous";
    }
}
