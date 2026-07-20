package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import com.takibo.managementservice.infrastructure.jpa.repository.OrganizationJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationReadAdapterTest {

    @Mock private OrganizationJpaRepository organizations;
    @Mock private JpaSpaceRepository spaces;

    @Test
    void getOrganizationContextForSpaceCreation_locksOrganizationBeforeCountingSpaces() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity organization = OrganizationEntity.builder()
                .id(orgId)
                .code("takibo")
                .name("Takibo")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(organizations.findByIdForUpdate(orgId)).thenReturn(Optional.of(organization));
        when(spaces.countByOrgId(orgId)).thenReturn(9);
        OrganizationReadAdapter adapter = new OrganizationReadAdapter(organizations, spaces);

        OrganizationContext result = adapter.getOrganizationContextForSpaceCreation(orgId);

        assertThat(result).isEqualTo(new OrganizationContext(orgId, true, 9));
        InOrder order = inOrder(organizations, spaces);
        order.verify(organizations).findByIdForUpdate(orgId);
        order.verify(spaces).countByOrgId(orgId);
        verify(organizations, never()).findById(orgId);
    }

    @Test
    void getOrganizationContextForSpaceCreation_unknownOrganizationDoesNotCountSpaces() {
        UUID orgId = UUID.randomUUID();
        when(organizations.findByIdForUpdate(orgId)).thenReturn(Optional.empty());
        OrganizationReadAdapter adapter = new OrganizationReadAdapter(organizations, spaces);

        assertThatThrownBy(() -> adapter.getOrganizationContextForSpaceCreation(orgId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(orgId.toString());

        verify(spaces, never()).countByOrgId(orgId);
    }
}
