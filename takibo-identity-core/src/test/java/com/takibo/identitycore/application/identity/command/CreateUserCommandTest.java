package com.takibo.identitycore.application.identity.command;

import com.takibo.identitycore.interfaces.rest.request.CreateUserRequestV2;
import com.takibo.identitycore.interfaces.rest.request.InitialAssignments;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateUserCommandTest {

    private static final UUID SPACE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Test
    void toString_doesNotExposeRawPassword() {
        CreateUserCommand command = CreateUserCommand.builder()
                .spaceId(SPACE_ID)
                .email("user@example.com")
                .rawPassword("s3cr3tP@ss!")
                .username("jdoe")
                .build();

        String result = command.toString();

        assertThat(result).doesNotContain("s3cr3tP@ss!");
        assertThat(result).doesNotContain("rawPassword");
    }

    @Test
    void builderToString_doesNotExposeRawPassword() {
        CreateUserCommand.CreateUserCommandBuilder builder = CreateUserCommand.builder()
                .spaceId(SPACE_ID)
                .email("user@example.com")
                .rawPassword("s3cr3tP@ss!")
                .username("jdoe");

        String result = builder.toString();

        assertThat(result).doesNotContain("s3cr3tP@ss!");
        assertThat(result).doesNotContain("rawPassword");
    }

    @Test
    void from_withInitialAssignments_mapsAllFields() {
        InitialAssignments assignments = new InitialAssignments(List.of("MANAGER", "EDITOR"), List.of("GRP_A"));
        CreateUserRequestV2 request = new CreateUserRequestV2(
                null, "user@example.com", "rawPass", "jdoe",
                "John", "Doe", assignments, null
        );

        CreateUserCommand command = CreateUserCommand.from(SPACE_ID, request);

        assertThat(command.spaceId()).isEqualTo(SPACE_ID);
        assertThat(command.email()).isEqualTo("user@example.com");
        assertThat(command.username()).isEqualTo("jdoe");
        assertThat(command.firstName()).isEqualTo("John");
        assertThat(command.lastName()).isEqualTo("Doe");
        assertThat(command.businessRoleCodes()).containsExactly("MANAGER", "EDITOR");
        assertThat(command.groupCodes()).containsExactly("GRP_A");
    }

    @Test
    void from_withNullInitialAssignments_defaultsToEmptyLists() {
        CreateUserRequestV2 request = new CreateUserRequestV2(
                null, "user@example.com", "rawPass", "jdoe",
                null, null, null, null
        );

        CreateUserCommand command = CreateUserCommand.from(SPACE_ID, request);

        assertThat(command.businessRoleCodes()).isEmpty();
        assertThat(command.groupCodes()).isEmpty();
    }

    @Test
    void from_withEmptyAssignmentLists_defaultsToEmpty() {
        InitialAssignments emptyAssignments = new InitialAssignments(null, null);
        CreateUserRequestV2 request = new CreateUserRequestV2(
                null, "user@example.com", "rawPass", "jdoe",
                null, null, emptyAssignments, null
        );

        CreateUserCommand command = CreateUserCommand.from(SPACE_ID, request);

        assertThat(command.businessRoleCodes()).isEmpty();
        assertThat(command.groupCodes()).isEmpty();
    }
}
