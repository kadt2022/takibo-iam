package com.takibo.identitycore.application.auth.service;

import com.takibo.identitycore.application.auth.model.AuthenticationMethod;
import com.takibo.identitycore.application.auth.model.HumanCredentials;
import com.takibo.identitycore.application.auth.model.TrustedOrganizationContext;
import com.takibo.identitycore.application.auth.model.VerifiedHuman;
import com.takibo.identitycore.application.auth.port.HumanAuthenticationCase;
import com.takibo.identitycore.domain.exception.AuthenticationFailedException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.IdentityStatus;
import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.vo.OrganizationId;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Vérification des credentials humains dans une Organization déjà résolue (TAS-GRANTS-03-A).
 * <p>
 * L'Account, ses credentials et son identité ne sont cherchés que dans l'Organization du
 * contexte : un même email dans une autre Organization n'entre jamais dans la décision. Une
 * identité absente, suspendue ou désactivée n'obtient aucune preuve (IAM-REVOKE-01).
 * Toutes les causes d'échec lèvent la même {@link AuthenticationFailedException} ; la cause réelle ne vit que dans les
 * journaux, et un hash factice égalise le temps de réponse quand aucun hash réel n'existe.
 * <p>
 * Volontairement PAS de {@code @Transactional} : l'incrément de {@code failedAttempts} est
 * persisté dans la transaction du repository et doit survivre à l'échec levé juste après.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HumanAuthenticationService implements HumanAuthenticationCase {

    private final AccountRepository accountRepository;
    private final AccountCredentialsRepository accountCredentialsRepository;
    private final PasswordHasherCase passwordHasher;
    private final TakiboIdentityRepository takiboIdentityRepository;
    private final Clock clock;

    @Value("${takibo.auth.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${takibo.auth.login.lock-seconds:900}")
    private long lockSeconds;

    /**
     * Hash factice pour l'égalisation temporelle. Configurable ; sinon généré UNE fois au
     * démarrage, jamais par requête.
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
    public VerifiedHuman authenticate(TrustedOrganizationContext context, HumanCredentials credentials) {
        Assert.notNull(context, "Trusted organization context must not be null");
        Assert.notNull(credentials, "Credentials must not be null");
        UUID orgId = context.orgId();
        OrganizationId organization = new OrganizationId(orgId);

        Optional<Account> account = accountRepository.findByEmail(organization, new EmailAddress(credentials.email()));
        if (account.isEmpty()) {
            throw refusedAfterDummyCheck(credentials, "unknown account", orgId);
        }
        Optional<AccountCredentials> stored = accountCredentialsRepository.find(organization, account.get().getId());
        if (stored.isEmpty()) {
            throw refusedAfterDummyCheck(credentials, "missing credentials", orgId);
        }
        AccountCredentials current = stored.get();

        if (current.isLocked()) {
            log.warn("Human authentication refused cause=account locked accountId={} orgId={}",
                    account.get().getId(), orgId);
            throw new AuthenticationFailedException();
        }
        if (!passwordHasher.matches(credentials.password(), current.getPasswordHash().getHash())) {
            accountCredentialsRepository.save(current.registerFailure(maxFailedAttempts, lockSeconds), orgId);
            log.warn("Human authentication refused cause=bad password accountId={} orgId={} attempts={}",
                    account.get().getId(), orgId, current.getFailedAttempts() + 1);
            throw new AuthenticationFailedException();
        }
        // IAM-REVOKE-01 : une identité absente ou non ACTIVE n'obtient aucune preuve. Contrôle
        // placé après le mot de passe : le coût du hash est déjà payé, aucun oracle temporel ; le
        // refus ne touche pas au compteur d'échecs.
        UUID accountId = account.get().getId().getValue();
        Optional<TakiboIdentity> identity = takiboIdentityRepository.findByOrgIdAndAccountId(orgId, accountId);
        if (identity.isEmpty() || identity.get().getIdentityStatus() != IdentityStatus.ACTIVE) {
            log.warn("Human authentication refused cause=identity not active status={} accountId={} orgId={}",
                    identity.map(TakiboIdentity::getIdentityStatus).map(Enum::name).orElse("ABSENT"), accountId, orgId);
            throw new AuthenticationFailedException();
        }
        if (current.getFailedAttempts() > 0 || current.getLockedUntil() != null) {
            accountCredentialsRepository.save(current.registerSuccess(), orgId);
        }
        return new VerifiedHuman(orgId, accountId, AuthenticationMethod.PASSWORD, clock.instant());
    }

    @Override
    public void equalizeFailedAttempt(HumanCredentials credentials) {
        passwordHasher.matches(credentials.password(), dummyPasswordHash);
    }

    private AuthenticationFailedException refusedAfterDummyCheck(HumanCredentials credentials, String cause,
                                                                 UUID orgId) {
        equalizeFailedAttempt(credentials);
        log.warn("Human authentication refused cause={} orgId={}", cause, orgId);
        return new AuthenticationFailedException();
    }
}
