package com.takibo.managementservice.application.command;

import com.takibo.managementservice.application.security.ActorSource;
import com.takibo.managementservice.interfaces.rest.request.CreateSpaceRequest;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateSpaceCommand(
        UUID orgId,
        UUID ownerAccountId,
        ActorSource source,
        String name,
        String code,
        String description
) {

    public CreateSpaceCommand {
        if (source == null) {
            source = ActorSource.SYSTEM;
        }
    }
    public CreateSpaceCommand(UUID orgId, String code, String name) {
        this(orgId, null, null, name, code, null);
    }

    public static CreateSpaceCommand from(UUID orgId,
                                          UUID ownerAccountId,
                                          ActorSource source,
                                          CreateSpaceRequest req) {
        return new CreateSpaceCommand(orgId, ownerAccountId, source, req.name(), req.code(), req.description());
    }

    public static CreateSpaceCommand from(UUID orgId, String code, String name) {
        return new CreateSpaceCommand(orgId, null, null, name, code, null);
    }
}
