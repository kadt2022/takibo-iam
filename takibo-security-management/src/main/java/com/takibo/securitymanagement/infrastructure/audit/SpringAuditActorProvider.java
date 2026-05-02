package com.takibo.securitymanagement.infrastructure.audit;

import com.takibo.audit.spi.AuditActorProvider;
import com.takibo.securitycontext.exception.TakiboSecurityContextNotAvailableException;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpringAuditActorProvider implements AuditActorProvider {

    private final CurrentTakiboSecurityContextProvider currentTakiboSecurityContextProvider;

    @Override
    public Optional<AuditActor> currentActor() {
        try {
            TakiboSecurityContext ctx = currentTakiboSecurityContextProvider.current();
            UUID accountId = extractUuid(ctx.attributes().get(StandardAttributeKeys.ACCOUNT_ID).orElse(null));
            UUID userId = extractUuid(ctx.attributes().get(StandardAttributeKeys.USER_ID).orElse(null));
            UUID orgId = extractUuid(ctx.tenant() != null ? ctx.tenant().organizationId() : null);
            UUID spaceId = extractUuid(ctx.tenant() != null ? ctx.tenant().spaceId() : null);

            if (accountId == null) {
                accountId = extractUuid(ctx.subject() != null ? ctx.subject().subjectId() : null);
            }

            String actorType = ctx.subject() != null ? ctx.subject().nature().name() : null;
            String actorSource = ctx.subject() != null ? ctx.subject().authenticationMethod().name() : null;

            return Optional.of(new AuditActor(accountId, userId, orgId, spaceId, actorType, actorSource));
        } catch (TakiboSecurityContextNotAvailableException e) {
            return Optional.empty();
        }
    }

    private UUID extractUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        String text = value.toString();
        if (text.isBlank()) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
