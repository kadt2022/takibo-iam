package com.takibo.managementservice.infrastructure.jpa.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ClientJpaRepositoryTest {

    @Test
    void secretRotationQuery_comparesAndIncrementsVersion() throws NoSuchMethodException {
        Method method = OAuth2ClientJpaRepository.class.getMethod(
                "updateSecretByIdAndOrgIdAndSpaceId",
                UUID.class,
                UUID.class,
                UUID.class,
                Long.class,
                String.class,
                Instant.class);

        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains("c.version = c.version + 1");
        assertThat(query).contains("and c.version = :expectedVersion");
    }
}
