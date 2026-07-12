package com.takibo.identitycore.interfaces.rest.response;

import java.util.List;
import java.util.UUID;

public record CurrentUserSpacesResponse(
        UUID organizationId,
        List<CurrentUserSpaceItemResponse> items
) {
    public CurrentUserSpacesResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
