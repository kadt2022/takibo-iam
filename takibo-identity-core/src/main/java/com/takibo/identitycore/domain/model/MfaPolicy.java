package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.MfaPolicyId;
import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MfaPolicy {
    @EqualsAndHashCode.Include @ToString.Include
    private final MfaPolicyId id;

    private final SpaceId spaceId;

    private boolean enabled;   // toggle global
    private boolean required;  // enforcement globale

    // Autorisations par type
    private boolean allowTotp;
    private boolean allowWebauthn;
    private boolean allowSms;
    private boolean allowEmail;
    private boolean allowPush;

    private String stepUpRules;   // JSON/YAML free-form
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public MfaPolicy(MfaPolicyId id, SpaceId spaceId, boolean enabled, boolean required,
                     boolean allowTotp, boolean allowWebauthn, boolean allowSms, boolean allowEmail, boolean allowPush,
                     String stepUpRules, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.enabled = enabled;
        this.required = required;
        this.allowTotp = allowTotp;
        this.allowWebauthn = allowWebauthn;
        this.allowSms = allowSms;
        this.allowEmail = allowEmail;
        this.allowPush = allowPush;
        this.stepUpRules = stepUpRules;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public void enforce() { this.required = true; this.enabled = true; }
    public void relax()   { this.required = false; }
}
