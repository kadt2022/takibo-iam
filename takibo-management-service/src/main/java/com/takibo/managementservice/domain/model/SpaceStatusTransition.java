package com.takibo.managementservice.domain.model;

public record SpaceStatusTransition(
        SpaceStatus current,
        SpaceStatus requested
) {

    public boolean changesStatus() {
        return current != requested;
    }
}
