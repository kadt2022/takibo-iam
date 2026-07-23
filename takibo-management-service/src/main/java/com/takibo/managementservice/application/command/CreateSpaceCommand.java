package com.takibo.managementservice.application.command;

import com.takibo.managementservice.domain.model.ActorSource;
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

    public static CreateSpaceCommand from(UUID orgId, String code, String name) {
        return new CreateSpaceCommand(orgId, null, null, name, code, null);
    }
}
