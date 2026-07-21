package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaOrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationWriteAdapterTest {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Mock
    private JpaOrganizationRepository repository;

    @Test
    void create_maps_the_domain_values_and_returns_the_persisted_result() {
        OrganizationEntity saved = OrganizationEntity.builder()
                .id(ORGANIZATION_ID)
                .code("takibo-iam")
                .name("Takibo")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(repository.saveAndFlush(any())).thenReturn(saved);

        var result = adapter().create(
                ORGANIZATION_ID, "takibo-iam", "Takibo", OrganizationStatus.ACTIVE);

        ArgumentCaptor<OrganizationEntity> entity = ArgumentCaptor.forClass(OrganizationEntity.class);
        verify(repository).saveAndFlush(entity.capture());
        assertThat(entity.getValue().getId()).isEqualTo(ORGANIZATION_ID);
        assertThat(entity.getValue().getCode()).isEqualTo("takibo-iam");
        assertThat(entity.getValue().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(result.id()).isEqualTo(ORGANIZATION_ID);
        assertThat(result.code()).isEqualTo("takibo-iam");
    }

    @Test
    void create_translates_a_concurrent_unique_constraint_collision() {
        DataIntegrityViolationException databaseConflict = new DataIntegrityViolationException(
                "duplicate organization code",
                new IllegalStateException("unique constraint uk_organizations_code_ci"));
        when(repository.saveAndFlush(any())).thenThrow(databaseConflict);

        assertThatThrownBy(() -> adapter().create(
                ORGANIZATION_ID, "takibo-iam", "Takibo", OrganizationStatus.ACTIVE))
                .isInstanceOf(OrganizationCodeAlreadyExistsException.class)
                .hasMessageContaining("takibo-iam")
                .hasCause(databaseConflict);
    }

    @Test
    void create_preserves_an_unrelated_integrity_failure() {
        DataIntegrityViolationException databaseFailure = new DataIntegrityViolationException(
                "check constraint ck_organizations_status");
        when(repository.saveAndFlush(any())).thenThrow(databaseFailure);

        assertThatThrownBy(() -> adapter().create(
                ORGANIZATION_ID, "takibo-iam", "Takibo", OrganizationStatus.ACTIVE))
                .isSameAs(databaseFailure);
    }

    @Test
    void existsByCode_delegates_to_the_repository() {
        when(repository.existsByCode("takibo-iam")).thenReturn(true);

        assertThat(adapter().existsByCode("takibo-iam")).isTrue();
    }

    private OrganizationWriteAdapter adapter() {
        return new OrganizationWriteAdapter(repository);
    }
}
