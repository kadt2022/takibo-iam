package com.takibo.identitycore.application.auth.service;

import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.application.auth.mapper.AuthMapper;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.application.auth.port.HumanLoginCase;
import com.takibo.identitycore.domain.exception.AccountLockedException;
import com.takibo.identitycore.domain.exception.AuthenticationFailedException;
import com.takibo.identitycore.domain.exception.InvalidCredentialsException;
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
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedOrgKey;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * Login humain : vérifie l'identité, puis demande la preuve au port
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
 * <p>
 * Volontairement PAS de {@code @Transactional} de classe : l'incrément de
 * {@code failedAttempts} est persisté dans la transaction du repository et doit survivre
 * à l'exception d'authentification levée juste après.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HumanLoginService implements HumanLoginCase {

    private final SpaceKeyResolutionCase spaceKeyResolution;
    private final SpaceContextVerifier spaceContextVerifier;
    private final AccountRepository accountRepository;
    private final AccountCredentialsRepository accountCredentialsRepository;
    private final PasswordHasherCase passwordHasher;
    private final UserRepository userRepository;
    private final EffectiveRbacQueryCase effectiveRbacQuery;
    private final HumanAccessTokenIssuer tokenIssuer;
    private final AuthMapper authMapper;

    @Value("${takibo.auth.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${takibo.auth.login.lock-seconds:900}")
    private long lockSeconds;

    /**
     * Hash factice pour l'égalisation temporelle (revue du sage, P1) : lorsqu'aucun
     * hash réel n'est disponible (organisation inconnue/inactive, space inaccessible,
     * compte ou credentials absents), un matches() est quand même exécuté pour que
     * le temps de réponse ne trahisse pas la cause. Configurable ; sinon généré UNE
     * fois au démarrage — jamais par requête.
     */
    @Value("${takibo.auth.login.dummy-password-hash:}")
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        if (dummyPasswordHash == null || dummyPasswordHash.isBlank()) {
            dummyPasswordHash = passwordHasher.hash("takibo-dummy-" + UUID.randomUUID());
        }
    }

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
            performDummyPasswordCheck(command.password());
            log.warn("Login refused cause={} detail={}", ex.getClass().getSimpleName(), ex.getMessage());
            throw new AuthenticationFailedException();
        } catch (InvalidCredentialsException | AccountLockedException
                 | UserNotMemberOfSpaceException | UserNotActiveException ex) {
            // Réponse externe unique ; la cause réelle reste côté serveur (logs + audit).
            log.warn("Login refused cause={} detail={}", ex.getClass().getSimpleName(), ex.getMessage());
            throw new AuthenticationFailedException();
        }
    }

    private void performDummyPasswordCheck(String rawPassword) {
        passwordHasher.matches(rawPassword, dummyPasswordHash);
    }

    private boolean hasSpaceCode(LoginCommand command) {
        return command.spaceCode() != null && !command.spaceCode().isBlank();
    }

    /** IAM 31 : l'organisation identifie le compte — aucun space n'entre en jeu. */
    private LoginResponse loginOrganizationScoped(LoginCommand command) {
        ResolvedOrgKey key = spaceKeyResolution.resolveActiveOrganization(command.orgCode());

        Account account = verifyAccountCredentials(key.orgId(), command.email(), command.password());

        EffectiveRbac effective = effectiveRbacQuery.effectiveOrgFor(
                key.orgId(), account.getId().getValue());

        HumanTokenRequest tokenRequest = HumanTokenRequest.organizationScoped(
                key.orgId(),
                account.getId().getValue(),
                effective.roles(),
                effective.groups(),
                effective.permissions()
        );

        LoginToken token = tokenIssuer.issue(tokenRequest);

        log.info("Human login succeeded scope=ORGANIZATION orgId={} accountId={}",
                key.orgId(), account.getId());

        return authMapper.toLoginResponse(token, tokenRequest);
    }

    /** Chemin transitoire à quatre champs — comportement historique conservé. */
    private LoginResponse loginSpaceScoped(LoginCommand command) {
        // 1) Frontière demandée : codes lisibles -> identifiants réels (TMS), space actif exigé.
        ResolvedSpaceKey key = spaceKeyResolution.resolve(command.orgCode(), command.spaceCode());
        spaceContextVerifier.validateSpaceContext(key.spaceId());

        // 2) Identité : account org-scoped, credentials, verrouillage, mot de passe.
        Account account = verifyAccountCredentials(key.orgId(), command.email(), command.password());

        // 3) Situation : pas de token SPACE sans user local dans le space demandé.
        User user = userRepository.findBySpaceAndAccount(new SpaceId(key.spaceId()), account.getId())
                .orElseThrow(() -> new UserNotMemberOfSpaceException(key.spaceId()));

        // 3b) Le statut du user est une frontière d'accès : seul ACTIVE reçoit une preuve.
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login refused: user not active userId={} status={} spaceId={}",
                    user.getId().value(), user.getStatus(), key.spaceId());
            throw new UserNotActiveException(user.getId().value(), user.getStatus());
        }

        // 4) Snapshot borné du pouvoir effectif au moment de l'authentification.
        EffectiveRbac effective = effectiveRbacQuery.effectiveFor(
                key.orgId(), key.spaceId(), account.getId().getValue());

        HumanTokenRequest tokenRequest = HumanTokenRequest.spaceScoped(
                key.orgId(),
                key.spaceId(),
                account.getId().getValue(),
                user.getId().value(),
                effective.roles(),
                effective.groups(),
                effective.permissions()
        );

        LoginToken token = tokenIssuer.issue(tokenRequest);

        log.info("Human login succeeded scope=SPACE orgId={} spaceId={} accountId={} userId={}",
                key.orgId(), key.spaceId(), account.getId(), user.getId().value());

        return authMapper.toLoginResponse(token, tokenRequest);
    }

    /**
     * Vérification d'identité commune aux deux portées : account org-scoped,
     * credentials présents, verrouillage, mot de passe, compteur d'échecs.
     * Toutes les causes credentials lèvent la même {@link InvalidCredentialsException}.
     */
    private Account verifyAccountCredentials(UUID orgId, String rawEmail, String rawPassword) {
        EmailAddress email = new EmailAddress(rawEmail);
        Account account = accountRepository.findByEmail(new OrganizationId(orgId), email)
                .orElseThrow(() -> {
                    performDummyPasswordCheck(rawPassword);
                    return new InvalidCredentialsException();
                });

        AccountCredentials credentials = accountCredentialsRepository
                .find(new OrganizationId(orgId), account.getId())
                .orElseThrow(() -> {
                    performDummyPasswordCheck(rawPassword);
                    return new InvalidCredentialsException();
                });

        if (credentials.isLocked()) {
            log.warn("Login refused: account locked accountId={} orgId={}", account.getId(), orgId);
            throw new AccountLockedException();
        }

        if (!passwordHasher.matches(rawPassword, credentials.getPasswordHash().getHash())) {
            accountCredentialsRepository.save(
                    credentials.registerFailure(maxFailedAttempts, lockSeconds), orgId);
            log.warn("Login failed: bad password accountId={} orgId={} attempts={}",
                    account.getId(), orgId, credentials.getFailedAttempts() + 1);
            throw new InvalidCredentialsException();
        }

        if (credentials.getFailedAttempts() > 0 || credentials.getLockedUntil() != null) {
            accountCredentialsRepository.save(credentials.registerSuccess(), orgId);
        }

        return account;
    }
}
