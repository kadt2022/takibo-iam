package com.takibo.identitycore.integration.space;

import com.takibo.identitycore.integration.space.port.SpaceManagementCase;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.SpaceContext;
import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceContextVerifier {

    private final SpaceStatusCheckerCase spaceStatusCheckerCase;
    private final SpaceManagementCase spaceManagementCase;

    public SpaceContext validateSpaceContext(UUID spaceId) {
        SpaceId space = new SpaceId(spaceId);

        spaceStatusCheckerCase.assertSpaceExistsAndActive(space.getValue());

        UUID organizationId = spaceManagementCase.findOrgIdBySpaceId(space)
                .orElseThrow(() -> new UserCreationException("Organization not found for space " + space.value()));

        return new SpaceContext(space, organizationId);
    }
}
