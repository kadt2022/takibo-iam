package com.takibo.managementservice.application.command;
import java.util.UUID;
public record CreateOrganizationCommand(UUID id, String code, String name) {}
