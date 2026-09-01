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

/**
 * Ligne {@code oauth2_authorization_consent} (TAS-GRANTS-02).
 * <p>
 * Clé de lecture globale sur {@code (registered_client_id, principal_name)} — voir
 * V202608290001 : {@code OAuth2AuthorizationConsentService.findById(registeredClientId,
 * principalName)} n'a aucun paramètre de tenant.
 */
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
                        name = "uk_oauth2_consent_client_principal_global",
                        columnNames = {"registered_client_id", "principal_name"}
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

    /** NULL = PLATFORM. */
    @Column(name = "org_id")
    private UUID orgId;

    /** NULL pour PLATFORM et ORGANIZATION ; requis pour SPACE. */
    @Column(name = "space_id")
    private UUID spaceId;

    /** {@code RegisteredClient.getId()}, jamais le {@code client_id} public. */
    @Column(name = "registered_client_id", nullable = false, length = 128)
    private String registeredClientId;

    /**
     * NULL tant qu'aucun port de résolution de compte par {@code principal_name} n'existe
     * (TAS-GRANTS-03) : {@code OAuth2AuthorizationConsentService} ne reçoit de Spring
     * Authorization Server que {@code registeredClientId}/{@code principalName}/
     * {@code authorities}, jamais un identifiant de compte — voir V202608290003. Ne porte pas
     * le sujet du couple de lecture, {@link #principalName} s'en charge.
     */
    @Column(name = "principal_account_id")
    private UUID principalAccountId;

    /** HUMAN dans tous les cas observés — un client_credentials ne consent jamais. */
    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    /** Clé de lecture de {@code findById}, avec {@link #registeredClientId}. */
    @Column(name = "principal_name", nullable = false, length = 255)
    private String principalName;

    @Column(name = "authorities", nullable = false, length = 2000)
    private String authorities;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
