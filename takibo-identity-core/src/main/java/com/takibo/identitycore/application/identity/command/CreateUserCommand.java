package com.takibo.identitycore.application.identity.command;

import com.takibo.identitycore.interfaces.rest.request.CreateUserRequestV2;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record CreateUserCommand(
        UUID spaceId,
        UUID accountId,
        String email,
        String rawPassword,
        String username,
        String firstName,
        String lastName,
        List<String> roleCodes,
        List<String> groupCodes,
        Map<String, Object> metadata
) {
    public static CreateUserCommand from(UUID spaceId, CreateUserRequestV2 r) {

        List<String> roleNames = r.initialAssignments() != null && r.initialAssignments().roleNames() != null
                ? r.initialAssignments().roleNames()
                : List.of();

        List<String> groupCodes = r.initialAssignments() != null && r.initialAssignments().groupCodes() != null
                ? r.initialAssignments().groupCodes()
                : List.of();

        return new CreateUserCommand(
                spaceId,
                r.accountId(),
                r.email(),
                r.rawPassword(),
                r.username(),
                r.firstName(),
                r.lastName(),
                roleNames,
                groupCodes,
                r.metadata()
        );
    }
}