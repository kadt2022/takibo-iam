package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.port.OrganizationReadPort;
import com.takibo.managementservice.domain.exception.SpaceQuotaExceededException;
import com.takibo.managementservice.domain.model.OrganizationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class OrganizationDomainServiceTest {

    @Mock private OrganizationReadPort organizationReadPort;
    @InjectMocks private OrganizationDomainService service;

    @Test
    void assertOrganizationAllowsSpaceCreation_usesLockedContext() {
        UUID orgId = UUID.randomUUID();
        OrganizationContext context = new OrganizationContext(orgId, true, 9);
        when(organizationReadPort.getOrganizationContextForSpaceCreation(orgId)).thenReturn(context);

        assertThat(service.assertOrganizationAllowsSpaceCreation(orgId)).isSameAs(context);

        verify(organizationReadPort).getOrganizationContextForSpaceCreation(orgId);
        verify(organizationReadPort, never()).getOrganizationContext(orgId);
    }

    @Test
    void assertOrganizationAllowsSpaceCreation_rejectsQuotaUnderSameLock() {
        UUID orgId = UUID.randomUUID();
        when(organizationReadPort.getOrganizationContextForSpaceCreation(orgId))
                .thenReturn(new OrganizationContext(orgId, true, 10));

        assertThatThrownBy(() -> service.assertOrganizationAllowsSpaceCreation(orgId))
                .isInstanceOf(SpaceQuotaExceededException.class);
    }
}
