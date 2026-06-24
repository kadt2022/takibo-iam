package com.takibo.identitycore.interfaces.rest.advice;

import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapping HTTP des exceptions de domaine de TIS-CORE.
 * <p>
 * Les exceptions du domaine restent neutres (aucune dépendance Spring Web) ; c'est ici,
 * dans la couche interfaces, qu'elles sont traduites en réponses HTTP.
 */
@RestControllerAdvice
public class IdentityRestExceptionHandler {

    @ExceptionHandler({OrganizationNotFoundException.class, SpaceNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }
}
