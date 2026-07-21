package com.takibo.managementservice.infrastructure.jpa.support;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConstraintViolationTest {

    @Test
    void mentions_finds_case_insensitive_constraint_in_nested_cause() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "insert failed",
                new IllegalStateException("violates unique constraint UQ_CLIENT_ID"));

        assertThat(DatabaseConstraintViolation.mentions(failure, "uq_client_id")).isTrue();
    }

    @Test
    void mentions_rejects_unrelated_or_missing_messages() {
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("foreign key failure");

        assertThat(DatabaseConstraintViolation.mentions(unrelated, "uq_client_id")).isFalse();
        assertThat(DatabaseConstraintViolation.mentions(new IllegalStateException(), "uq_client_id")).isFalse();
    }
}
