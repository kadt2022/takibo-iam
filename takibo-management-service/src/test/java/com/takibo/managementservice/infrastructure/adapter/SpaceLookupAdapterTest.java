package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceLookupAdapterTest {

    @Test
    void delegates_queries_to_the_jpa_repository() {
        UUID spaceId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        UUID organizationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        JpaSpaceRepository repository = mock(JpaSpaceRepository.class);
        when(repository.existsById(spaceId)).thenReturn(true);
        when(repository.findOrgIdById(spaceId)).thenReturn(Optional.of(organizationId));
        SpaceLookupAdapter adapter = new SpaceLookupAdapter(repository);

        assertThat(adapter.existsById(spaceId)).isTrue();
        assertThat(adapter.findOrganizationId(spaceId)).contains(organizationId);
    }
}
