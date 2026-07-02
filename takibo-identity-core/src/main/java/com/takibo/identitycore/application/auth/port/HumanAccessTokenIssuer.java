package com.takibo.identitycore.application.auth.port;

import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;

/**
 * Port outbound : émission de la preuve pour une identité humaine déjà vérifiée.
 * <p>
 * TIS-CORE décide si l'humain peut recevoir la preuve ; il ne la fabrique jamais.
 * L'implémentation vit dans le boot et délègue au signeur de TAS.
 */
public interface HumanAccessTokenIssuer {
    LoginToken issue(HumanTokenRequest request);
}
