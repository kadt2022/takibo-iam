package com.takibo.identitycore.interfaces.rest.advice;

import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRestExceptionHandlerTest {

    private final IdentityRestExceptionHandler handler = new IdentityRestExceptionHandler();

    @Test
    void given_organization_not_found_when_handle_not_found_then_returns_not_found_problem_detail() {
        ProblemDetail problem = handler.handleNotFound(
                new OrganizationNotFoundException("Organization not found: takibo-iam"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Organization not found: takibo-iam");
    }

    @Test
    void given_space_not_found_when_handle_not_found_then_returns_not_found_problem_detail() {
        ProblemDetail problem = handler.handleNotFound(
                new SpaceNotFoundException("Space not found: finance"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Space not found: finance");
    }
}
