package com.takibo.identitycore.application.auth.service;

import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.application.auth.mapper.AuthMapper;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.application.auth.port.HumanLoginCase;
import com.takibo.identitycore.domain.exception.AccountLockedException;
import com.takibo.identitycore.domain.exception.InvalidCredentialsException;
import com.takibo.identitycore.domain.exception.UserNotActiveException;
import com.takibo.identitycore.domain.exception.UserNotMemberOfSpaceException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Login humain situé : vérifie l'identité, puis demande la preuve au port
 * {@link HumanAccessTokenIssuer}. TIS-CORE ne signe rien.
 * <p>
 * Toutes les causes d'échec liées aux credentials (email inconnu dans l'org, credentials
 * absents, mauvais mot de passe) lèvent la même {@link InvalidCredentialsException} —
 * aucune distinction observable par l'appelant.
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
    private final GovernanceRoleAssignmentRepository roleAssignments;
    private final HumanAccessTokenIssuer tokenIssuer;
    private final AuthMapper authMapper;

    @Value("${takibo.auth.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${takibo.auth.login.lock-seconds:900}")
    private long lockSeconds;

    @Override
    public LoginResponse login(LoginCommand command) {
        Assert.notNull(command, "Login command must not be null");

        // 1) Frontière demandée : codes lisibles -> identifiants réels (TMS), space actif exigé.
        ResolvedSpaceKey key = spaceKeyResolution.resolve(command.orgCode(), command.spaceCode());
        spaceContextVerifier.validateSpaceContext(key.spaceId());

        // 2) Identité : account org-scoped, credentials, verrouillage, mot de passe.
        EmailAddress email = new EmailAddress(command.email());
        Account account = accountRepository.findByEmail(new OrganizationId(key.orgId()), email)
                .orElseThrow(InvalidCredentialsException::new);

        AccountCredentials credentials = accountCredentialsRepository
                .find(new OrganizationId(key.orgId()), account.getId())
                .orElseThrow(InvalidCredentialsException::new);

        if (credentials.isLocked()) {
            log.warn("Login refused: account locked accountId={} orgId={}", account.getId(), key.orgId());
            throw new AccountLockedException();
        }

        if (!passwordHasher.matches(command.password(), credentials.getPasswordHash().getHash())) {
            accountCredentialsRepository.save(
                    credentials.registerFailure(maxFailedAttempts, lockSeconds), key.orgId());
            log.warn("Login failed: bad password accountId={} orgId={} attempts={}",
                    account.getId(), key.orgId(), credentials.getFailedAttempts() + 1);
            throw new InvalidCredentialsException();
        }

        if (credentials.getFailedAttempts() > 0 || credentials.getLockedUntil() != null) {
            accountCredentialsRepository.save(credentials.registerSuccess(), key.orgId());
        }

        // 3) Situation : pas de token SPACE sans user local dans le space demandé.
        User user = userRepository.findBySpaceAndAccount(new SpaceId(key.spaceId()), account.getId())
                .orElseThrow(() -> new UserNotMemberOfSpaceException(key.spaceId()));

        // 3b) Le statut du user est une frontière d'accès : seul ACTIVE reçoit une preuve.
        //     (Password déjà prouvé : révéler l'état local n'est plus un oracle.)
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login refused: user not active userId={} status={} spaceId={}",
                    user.getId().value(), user.getStatus(), key.spaceId());
            throw new UserNotActiveException(user.getId().value(), user.getStatus());
        }

        // 4) Snapshot minimal des rôles techniques (pas le RBAC complet dans le JWT).
        List<String> roles = roleAssignments.findAssignedTechnicalRoleCodes(
                key.orgId(), key.spaceId(), account.getId().getValue());

        HumanTokenRequest tokenRequest = new HumanTokenRequest(
                key.orgId(),
                key.spaceId(),
                account.getId().getValue(),
                user.getId().value(),
                roles
        );

        LoginToken token = tokenIssuer.issue(tokenRequest);

        log.info("Human login succeeded orgId={} spaceId={} accountId={} userId={}",
                key.orgId(), key.spaceId(), account.getId(), user.getId().value());

        return authMapper.toLoginResponse(token, tokenRequest);
    }
}
