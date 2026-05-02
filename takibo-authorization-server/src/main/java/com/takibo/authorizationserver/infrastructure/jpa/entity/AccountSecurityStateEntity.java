package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.OffsetDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "account_security_state",
        indexes = {
                @Index(name = "idx_ass_org", columnList = "org_id"),
                @Index(name = "idx_ass_org_epoch", columnList = "org_id, current_epoch")
        }
)
public class AccountSecurityStateEntity {

    @EmbeddedId
    private AccountSecurityStateId id;

    @Column(name = "current_epoch", nullable = false)
    private int currentEpoch;

    @Column(name = "last_bump_reason", length = 255)
    private String lastBumpReason;

    @Column(name = "last_bump_at")
    private OffsetDateTime lastBumpAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
