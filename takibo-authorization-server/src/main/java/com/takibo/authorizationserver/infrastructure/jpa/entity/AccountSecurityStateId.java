package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class AccountSecurityStateId implements Serializable {

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountSecurityStateId that)) return false;
        return Objects.equals(orgId, that.orgId) && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, accountId);
    }
}
