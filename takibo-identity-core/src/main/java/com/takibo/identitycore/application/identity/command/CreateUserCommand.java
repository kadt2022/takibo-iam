package com.takibo.identitycore.application.identity.command;

import com.takibo.audit.annotations.Sensitive;
import com.takibo.identitycore.interfaces.rest.request.CreateUserRequest;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record CreateUserCommand(
        UUID spaceId,
        UUID accountId,
        String email,
        @Sensitive
        String rawPassword,
        String username,
        String firstName,
        String lastName,
        List<String> businessRoleCodes,
        List<String> initialBusinessGroupCodes,
        Map<String, Object> metadata
) {
    public CreateUserCommand {
        businessRoleCodes = businessRoleCodes != null ? businessRoleCodes : List.of();
        initialBusinessGroupCodes = initialBusinessGroupCodes != null ? initialBusinessGroupCodes : List.of();
    }
    public static CreateUserCommand from(UUID spaceId, CreateUserRequest r) {

        List<String> businessRoleCodes = r.initialAssignments() != null && r.initialAssignments().roleNames() != null
                ? r.initialAssignments().roleNames()
                : List.of();

        List<String> initialBusinessGroupCodes = r.initialAssignments() != null && r.initialAssignments().initialBusinessGroupCodes() != null
                ? r.initialAssignments().initialBusinessGroupCodes()
                : List.of();

        return new CreateUserCommand(
                spaceId,
                r.accountId(),
                r.email(),
                r.rawPassword(),
                r.username(),
                r.firstName(),
                r.lastName(),
                businessRoleCodes,
                initialBusinessGroupCodes,
                r.metadata()
        );
    }

    @Override
    public String toString() {
        return "CreateUserCommand[spaceId=" + spaceId +
                ", accountId=" + accountId +
                ", email=" + email +
                ", username=" + username +
                ", firstName=" + firstName +
                ", lastName=" + lastName +
                ", businessRoleCodes=" + businessRoleCodes +
                ", initialBusinessGroupCodes=" + initialBusinessGroupCodes +
                ", metadata=" + metadata + "]";
    }

    public static class CreateUserCommandBuilder {
        @Override
        public String toString() {
            return "CreateUserCommand.CreateUserCommandBuilder(spaceId=" + spaceId +
                    ", accountId=" + accountId +
                    ", email=" + email +
                    ", username=" + username +
                    ", firstName=" + firstName +
                    ", lastName=" + lastName +
                    ", businessRoleCodes=" + businessRoleCodes +
                    ", initialBusinessGroupCodes=" + initialBusinessGroupCodes +
                    ", metadata=" + metadata + ")";
        }
    }
}
