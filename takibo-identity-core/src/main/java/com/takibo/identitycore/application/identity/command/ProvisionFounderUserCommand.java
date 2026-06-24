package com.takibo.identitycore.application.identity.command;

import java.util.UUID;

/**
 * Commande de provisioning du fondateur lors du bootstrap d'une organisation.
 * <p>
 * Contrairement à {@link CreateUserCommand} utilisée par le flux normal, le fondateur
 * est créé dans une org/space qui viennent d'être établis : on fournit donc
 * explicitement {@code organizationId} et {@code spaceId}, sans dépendre d'un
 * contexte d'organisation courant (token).
 */
public record ProvisionFounderUserCommand(
        UUID organizationId,
        UUID spaceId,
        UUID accountId,
        String username,
        String firstName,
        String lastName
) {}
