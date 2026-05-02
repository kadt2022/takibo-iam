package com.takibo.managementservice.application.command;

import java.util.UUID;

public record CreateSpaceResult(
    UUID spaceId,
    String code,
    String name
) {}
