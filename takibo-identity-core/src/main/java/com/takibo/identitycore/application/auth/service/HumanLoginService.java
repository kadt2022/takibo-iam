package com.takibo.identitycore.application.auth.service;

import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.application.auth.mapper.AuthMapper;
import com.takibo.identitycore.application.auth.model.HumanCredentials;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;
import com.takibo.identitycore.application.auth.model.TrustedOrganizationContext;
import com.takibo.identitycore.application.auth.model.VerifiedHuman;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.application.auth.port.HumanAuthenticationCase;
import com.takibo.identitycore.application.auth.port.HumanLoginCase;
import com.takibo.identitycore.domain.exception.AuthenticationFailedException;
import com.takibo.identitycore.domain.exception.OrganizationNotActiveException;
import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceDisabledException;
import com.takibo.identitycore.domain.exception.SpaceGuardException;
import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.exception.UserNotActiveException;
import com.takibo.identitycore.domain.exception.UserNotMemberOfSpaceException;
import com.takibo.identitycore.application.rbac.effective.model.EffectiveRbac;
import com.takibo.identitycore.application.rbac.effective.port.in.EffectiveRbacQueryCase;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedOrgKey;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Login humain : résout l'Organization, fait vérifier l'identité par le cœur unique
 * {@link HumanAuthenticationCase} (TAS-GRANTS-03-A), puis demande la preuve au port
 * {@link HumanAccessTokenIssuer}. TIS-CORE ne signe rien.
 * <p>
 * Deux portées (IAM 31) :
 * <ul>
 *   <li><b>ORGANIZATION</b> — {@code orgCode + email + password} : l'organisation
 *       identifie le compte. Le token porte le pouvoir organisationnel effectif
 *       (attributions org-level uniquement) — jamais celui d'un space.</li>
 *   <li><b>SPACE</b> — chemin transitoire à quatre champs, inchangé : {@code spaceCode}
 *       présent, user local actif exigé, pouvoir effectif situé. Son retrait sera
 *       acté par le récit IAM 33 (échange de contexte ORG → SPACE).</li>
 * </ul>
 * La surface de login ne raconte rien (IAM 31, arbitrage B) : organisation inexistante ou
 * inactive, space inaccessible, compte inconnu, mauvais mot de passe, compte verrouillé,
 * user non actif — toutes les causes convergent vers {@link AuthenticationFailedException}
 * (401 uniforme). La cause réelle vit dans les logs/audit, jamais dans la réponse.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HumanLoginService implements HumanLoginCase {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final SpaceContextVerifier spaceContextVerifier;
    private final HumanAuthenticationCase authentication;
    private final UserRepository userRepository;
    private final EffectiveRbacQueryCase effectiveRbacQuery;
    private final HumanAccessTokenIssuer tokenIssuer;
    private final AuthMapper authMapper;

    @Override
    public LoginResponse login(LoginCommand command) {
        Assert.notNull(command, "Login command must not be null");

        try {
            return hasSpaceCode(command) ? loginSpaceScoped(command) : loginOrganizationScoped(command);
        } catch (OrganizationNotFoundException | OrganizationNotActiveException
                 | SpaceNotFoundException | SpaceNotActiveException | SpaceDisabledException
                 | SpaceGuardException ex) {
            // Échec de résolution AVANT toute lecture de credentials : le coût du
            // hash n'a pas été payé — on l'égalise pour fermer l'oracle temporel.
            authentication.equalizeFailedAttempt(credentialsOf(command));
            log.warn("Login refused cause={} detail={}", ex.getClass().getSimpleName(), ex.getMessage());
            throw new AuthenticationFailedException();
        } catch (UserNotMemberOfSpaceException | UserNotActiveException ex) {
            // Réponse externe unique ; la cause réelle reste côté serveur (logs + audit).
            log.warn("Login refused cause={} detail={}", ex.getClass().getSimpleName(), ex.getMessage());
            throw new AuthenticationFailedException();
        }
    }

    private static HumanCredentials credentialsOf(LoginCommand command) {
        return new HumanCredentials(command.email(), command.password());
    }

    private boolean hasSpaceCode(LoginCommand command) {
        return command.spaceCode() != null && !command.spaceCode().isBlank();
    }

    /** IAM 31 : l'organisation identifie le compte — aucun space n'entre en jeu. */
    private LoginResponse loginOrganizationScoped(LoginCommand command) {
        ResolvedOrgKey key = spaceKeyResolution.resolveActiveOrganization(command.orgCode());

        VerifiedHuman human = authentication.authenticate(TrustedOrganizationContext.from(key), credentialsOf(command));

        EffectiveRbac effective = effectiveRbacQuery.effectiveOrgFor(key.orgId(), human.accountId());

        HumanTokenRequest tokenRequest = HumanTokenRequest.organizationScoped(
                key.orgId(),
                human.accountId(),
                effective.roles(),
                effective.groups(),
                effective.permissions()
        );

        LoginToken token = tokenIssuer.issue(tokenRequest);

        log.info("Human login succeeded scope=ORGANIZATION orgId={} accountId={}",
                key.orgId(), human.accountId());

        return authMapper.toLoginResponse(token, tokenRequest);
    }

    /** Chemin transitoire à quatre champs — comportement historique conservé. */
    private LoginResponse loginSpaceScoped(LoginCommand command) {
        // 1) Frontière demandée : codes lisibles -> identifiants réels (TMS), space actif exigé.
        ResolvedSpaceKey key = spaceKeyResolution.resolve(command.orgCode(), command.spaceCode());
        spaceContextVerifier.validateSpaceContext(key.spaceId());

        // 2) Identité : cœur unique, dans l'Organization du space résolu.
        VerifiedHuman human = authentication.authenticate(TrustedOrganizationContext.from(key), credentialsOf(command));
        AccountId accountId = new AccountId(human.accountId());

        // 3) Situation : pas de token SPACE sans user local dans le space demandé.
        User user = userRepository.findBySpaceAndAccount(new SpaceId(key.spaceId()), accountId)
                .orElseThrow(() -> new UserNotMemberOfSpaceException(key.spaceId()));

        // 3b) Le statut du user est une frontière d'accès : seul ACTIVE reçoit une preuve.
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login refused: user not active userId={} status={} spaceId={}",
                    user.getId().value(), user.getStatus(), key.spaceId());
            throw new UserNotActiveException(user.getId().value(), user.getStatus());
        }

        // 4) Snapshot borné du pouvoir effectif au moment de l'authentification.
        EffectiveRbac effective = effectiveRbacQuery.effectiveFor(
                key.orgId(), key.spaceId(), human.accountId());

        HumanTokenRequest tokenRequest = HumanTokenRequest.spaceScoped(
                key.orgId(),
                key.spaceId(),
                human.accountId(),
                user.getId().value(),
                effective.roles(),
                effective.groups(),
                effective.permissions()
        );

        LoginToken token = tokenIssuer.issue(tokenRequest);

        log.info("Human login succeeded scope=SPACE orgId={} spaceId={} accountId={} userId={}",
                key.orgId(), key.spaceId(), human.accountId(), user.getId().value());

        return authMapper.toLoginResponse(token, tokenRequest);
    }
}
