package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.exception.OrganizationCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.application.port.OrganizationWritePort;
import com.takibo.managementservice.application.result.OrganizationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationApplicationServiceTest {

    @Mock private OrganizationWritePort organizations;

    @InjectMocks private OrganizationApplicationService service;

    @Test
    void given_new_organization_when_create_then_saves_normalized_active_organization() {
        when(organizations.existsByCode("takibo-iam")).thenReturn(false);
        when(organizations.create(
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.eq("takibo-iam"),
                org.mockito.ArgumentMatchers.eq("Takibo"),
                org.mockito.ArgumentMatchers.eq(OrganizationStatus.ACTIVE)))
                .thenAnswer(invocation -> new OrganizationResult(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)));

        var result = service.create("Takibo IAM", "Takibo");

        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(organizations).create(id.capture(), org.mockito.ArgumentMatchers.eq("takibo-iam"),
                org.mockito.ArgumentMatchers.eq("Takibo"), org.mockito.ArgumentMatchers.eq(OrganizationStatus.ACTIVE));

        assertThat(id.getValue()).isNotNull();
        assertThat(result.id()).isEqualTo(id.getValue());
        assertThat(result.code()).isEqualTo("takibo-iam");
        assertThat(result.name()).isEqualTo("Takibo");
    }

    @Test
    void given_existing_normalized_code_when_create_then_throws_code_already_exists() {
        when(organizations.existsByCode("takibo-iam")).thenReturn(true);

        assertThatThrownBy(() -> service.create("Takibo IAM", "Takibo"))
                .isInstanceOf(OrganizationCodeAlreadyExistsException.class)
                .hasMessageContaining("takibo-iam");

        verify(organizations, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
