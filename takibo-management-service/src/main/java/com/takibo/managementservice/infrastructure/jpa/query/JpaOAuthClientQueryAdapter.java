package com.takibo.managementservice.infrastructure.jpa.query;

import com.takibo.managementservice.application.query.port.OAuthClientQueryCase;
import com.takibo.managementservice.infrastructure.jpa.repository.OAuth2ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaOAuthClientQueryAdapter implements OAuthClientQueryCase {

    private final OAuth2ClientJpaRepository clients;

    @Override
    public long countClients(UUID orgId) {
        // Compteur strictement situé par org_id : un client d'une autre
        // organisation n'entre jamais dans ce total.
        return clients.countByOrgId(orgId);
    }
}
