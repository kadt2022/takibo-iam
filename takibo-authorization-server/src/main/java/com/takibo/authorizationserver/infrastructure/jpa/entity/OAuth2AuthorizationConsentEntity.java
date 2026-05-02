package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "oauth2_authorization_consent",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_oauth2_consent_client_principal",
                        columnNames = {"org_id", "space_id", "registered_client_id", "principal_account_id"}
                )
        },
        indexes = {
                @Index(name = "idx_oauth2_consent_org_space", columnList = "org_id, space_id"),
                @Index(name = "idx_oauth2_consent_client", columnList = "org_id, space_id, registered_client_id"),
                @Index(name = "idx_oauth2_consent_account", columnList = "org_id, principal_account_id")
        }
)
public class OAuth2AuthorizationConsentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "registered_client_id", nullable = false, length = 128)
    private String registeredClientId;

    @Column(name = "principal_account_id", nullable = false)
    private UUID principalAccountId;

    @Column(name = "authorities", nullable = false, length = 2000)
    private String authorities;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
