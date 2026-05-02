package com.takibo.identitycore.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Métadonnées d’attribution (timestamp + auteur). assignedBy peut être null. */
public record AssignmentMeta(Instant assignedAt, UUID assignedBy) { }

