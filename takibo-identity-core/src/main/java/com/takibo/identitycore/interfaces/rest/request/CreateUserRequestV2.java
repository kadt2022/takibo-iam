package com.takibo.identitycore.interfaces.rest.request;


import com.takibo.audit.annotations.*;
import com.takibo.audit.infrastructure.service.MaskingLogger;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;


/**
 * Payload V2 — either provide accountId OR (email + rawPassword).
 * Cross-field validation is enforced in the application service for simplicity.
 */
public record CreateUserRequestV2(


        UUID accountId,

        @Mask(mode = MaskMode.LEFT, showLeft = 1, showRight = 7)
        @Email String email,

        @Sensitive // prime sur tout : ne sortira jamais en clair
        @Mask(mode = MaskMode.FULL)
        String rawPassword,

        @Mask(mode = MaskMode.CENTER, showLeft = 1, showRight = 1)
        String username,

        String firstName,

        @AuditIgnore // totalement omis des logs/audits
        String lastName,

        InitialAssignments initialAssignments,

        Map<String, Object> metadata
) {

        @Override
        public String toString() {
                // Délégué au moteur de masquage Takibo.
                return MaskingLogger.safeToString(this);
        }
}