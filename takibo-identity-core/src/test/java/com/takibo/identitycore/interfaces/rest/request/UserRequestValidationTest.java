package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void updateUserProfileRequest_acceptsValidPartialPatch() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("alice", "Alice", "Martin", Map.of("team", "ops"));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.username()).isEqualTo("alice");
        assertThat(request.metadata()).containsEntry("team", "ops");
    }

    @Test
    void updateUserProfileRequest_rejectsBlankUsernameBecauseMinimumSizeIsOne() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("", null, null, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void userStatusChangeRequest_acceptsNullReason() {
        UserStatusChangeRequest request = new UserStatusChangeRequest(null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.reason()).isNull();
    }

    @Test
    void userStatusChangeRequest_rejectsOversizedReason() {
        UserStatusChangeRequest request = new UserStatusChangeRequest("x".repeat(501));

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
