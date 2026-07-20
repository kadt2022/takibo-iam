package com.takibo.managementservice.infrastructure.jpa.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationJpaRepositoryTest {

    @Test
    void findByIdForUpdate_usesPessimisticWriteLock() throws NoSuchMethodException {
        Method method = OrganizationJpaRepository.class.getMethod("findByIdForUpdate", UUID.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
