package com.takibo.managementservice.integration;

import com.takibo.managementservice.application.port.TechnicalRbacProvisioningPort;
import com.takibo.managementservice.domain.model.ActorSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpaceRbacProvisioningAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID CREATOR_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private final TechnicalRbacProvisioningPort rbac = mock(TechnicalRbacProvisioningPort.class);
    private final SpaceRbacProvisioningAdapter adapter = new SpaceRbacProvisioningAdapter(rbac);

    @ParameterizedTest
    @EnumSource(ActorSource.class)
    void grantsTheSpaceCreatorRoleInsideTheSpaceOnBehalfOfTheActor(ActorSource source) {
        adapter.provisionSpaceAdmin(ORG_ID, SPACE_ID, CREATOR_ID, source);

        verify(rbac).provisionSpaceCreator(ORG_ID, SPACE_ID, CREATOR_ID, source.name());
    }

    @Test
    void anUnknownActorIsRecordedAsTheSystem() {
        adapter.provisionSpaceAdmin(ORG_ID, SPACE_ID, CREATOR_ID, null);

        verify(rbac).provisionSpaceCreator(ORG_ID, SPACE_ID, CREATOR_ID, "SYSTEM");
    }
}
