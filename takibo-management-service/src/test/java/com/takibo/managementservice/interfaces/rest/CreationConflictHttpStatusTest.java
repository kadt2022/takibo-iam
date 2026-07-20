package com.takibo.managementservice.interfaces.rest;

import com.takibo.managementservice.domain.exception.ClientAlreadyExistsException;
import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.exception.SpaceCodeAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CreationConflictHttpStatusTest {

    @Test
    void creationConflictsMapToHttp409() {
        assertConflict(OrganizationCodeAlreadyExistsException.class);
        assertConflict(SpaceCodeAlreadyExistsException.class);
        assertConflict(ClientAlreadyExistsException.class);
    }

    private static void assertConflict(Class<? extends RuntimeException> type) {
        ResponseStatus annotation = type.getAnnotation(ResponseStatus.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(HttpStatus.CONFLICT);
    }
}
