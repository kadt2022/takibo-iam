package com.takibo.iamboot.security;

import com.takibo.authorizationserver.infrastructure.springauthserver.token.HumanTokenCommand;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.HumanTokenSigner;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.SignedHumanToken;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import org.springframework.stereotype.Component;

/**
 * Câblage pur : traduit le port TIS-CORE vers le signeur TAS.
 * Boot ne fabrique pas la preuve — il ne construit aucun claim, ne choisit aucun TTL.
 */
@Component
public class BootHumanAccessTokenIssuer implements HumanAccessTokenIssuer {

    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final HumanTokenSigner humanTokenSigner;

    public BootHumanAccessTokenIssuer(HumanTokenSigner humanTokenSigner) {
        this.humanTokenSigner = humanTokenSigner;
    }

    @Override
    public LoginToken issue(HumanTokenRequest request) {
        SignedHumanToken signed = humanTokenSigner.sign(new HumanTokenCommand(
                request.orgId(),
                request.spaceId(),
                request.accountId(),
                request.userId(),
                request.roles(),
                request.groups(),
                request.permissions()
        ));
        return new LoginToken(signed.tokenValue(), TOKEN_TYPE_BEARER, signed.expiresInSeconds());
    }
}
