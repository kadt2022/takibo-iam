package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaOrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationApplicationServiceTest {

    @Mock private JpaOrganizationRepository organizations;

    @InjectMocks private OrganizationApplicationService service;

    @Test
    void normalizes_code_and_saves_active_organization() {
        when(organizations.existsByCode("takibo-iam")).thenReturn(false);

        var result = service.create("Takibo IAM", "Takibo");

        ArgumentCaptor<OrganizationEntity> captor = ArgumentCaptor.forClass(OrganizationEntity.class);
        verify(organizations).saveAndFlush(captor.capture());
        OrganizationEntity saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).isEqualTo("takibo-iam");
        assertThat(saved.getName()).isEqualTo("Takibo");
        assertThat(saved.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(result.id()).isEqualTo(saved.getId());
        assertThat(result.code()).isEqualTo("takibo-iam");
        assertThat(result.name()).isEqualTo("Takibo");
    }

    @Test
    void refuses_existing_normalized_code() {
        when(organizations.existsByCode("takibo-iam")).thenReturn(true);

        assertThatThrownBy(() -> service.create("Takibo IAM", "Takibo"))
                .isInstanceOf(OrganizationCodeAlreadyExistsException.class)
                .hasMessageContaining("takibo-iam");

        verify(organizations, never()).saveAndFlush(org.mockito.Mockito.any());
    }
}
