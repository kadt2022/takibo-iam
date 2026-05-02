package com.takibo.managementservice.domain.model;

import com.takibo.managementservice.domain.vo.SpaceId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public final class Space {

  @EqualsAndHashCode.Include
  private final SpaceId id;

  private final UUID orgId;

  private final UUID ownerAccountId;

  private final String code;
  private final String name;
  private final String description;

  private final SpaceStatus status;

  private final Instant createdAt;
  private final Instant updatedAt;

  private final String statusReason;
  private final Instant statusUpdatedAt;

  @Builder.Default
  private final Long version = 0L;

  // ===== FACTORY METHODS =====

  public static Space createNew(String code,
                                UUID orgId,
                                String name,
                                String description) {
    Instant now = Instant.now();
    return createNew(SpaceId.newId(), orgId, null, code, name, description, now);
  }

  public static Space createNew(String code,
                                UUID orgId,
                                UUID ownerAccountId,
                                String name,
                                String description) {
    Instant now = Instant.now();
    return createNew(SpaceId.newId(), orgId, ownerAccountId, code, name, description, now);
  }

  public static Space createNew(SpaceId id,
                                UUID orgId,
                                UUID ownerAccountId,
                                String code,
                                String name,
                                String description,
                                Instant now) {

    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(orgId, "orgId");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(now, "now");

    return Space.builder()
            .id(id)
            .orgId(orgId)
            .ownerAccountId(ownerAccountId)
            .code(code)
            .name(name)
            .description(description)
            .status(SpaceStatus.ACTIVE)
            .statusReason(null)
            .statusUpdatedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .version(0L)
            .build();
  }

  // ===== BUSINESS METHODS =====

  public Space updateStatus(SpaceStatus newStatus, String reason) {
    return this.toBuilder()
            .status(newStatus)
            .statusReason(reason)
            .statusUpdatedAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  public Space updateInfo(String name, String description) {
    return this.toBuilder()
            .name(name)
            .description(description)
            .updatedAt(Instant.now())
            .build();
  }
}