package com.takibo.messaging.testsupport;

import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Double de test en memoire de {@link MessageDeliveryRepository}.
 * <p>
 * Le module ne dispose ni de Mockito ni d'une base embarquee : ce double, construit par
 * {@link Proxy}, n'implemente que les operations utilisees par le code de production et
 * enregistre les appels pour verification. Toute autre methode leve
 * {@link UnsupportedOperationException} afin de signaler un usage inattendu.
 */
public final class FakeMessageDeliveryRepository {

    public record StatusUpdate(UUID id, DeliveryStatus status, Instant now) { }

    public record AttemptUpdate(UUID id, DeliveryStatus status, int attempts, Instant nextRunAt,
                                String lastError, Instant now) { }

    public record ClaimCall(Instant now, Instant staleBefore, String lockedBy, int batchSize) { }

    public record EligibleCall(Instant now, Instant staleBefore) { }

    public final List<MessageDeliveryEntity> saved = new ArrayList<>();
    public final List<StatusUpdate> statusUpdates = new ArrayList<>();
    public final List<AttemptUpdate> attemptUpdates = new ArrayList<>();
    public final List<ClaimCall> claimCalls = new ArrayList<>();
    public final List<EligibleCall> eligibleCalls = new ArrayList<>();
    public final Map<DeliveryStatus, Long> countsByStatus = new EnumMap<>(DeliveryStatus.class);
    public int flushCount;

    /** Retourne une exception a lever pour l'entite sauvegardee, ou {@code null} pour accepter. */
    public Function<MessageDeliveryEntity, RuntimeException> saveFailure = entity -> null;
    public List<MessageDeliveryEntity> claimResult = List.of();
    public boolean eligible;

    private final MessageDeliveryRepository proxy;

    public FakeMessageDeliveryRepository() {
        this.proxy = (MessageDeliveryRepository) Proxy.newProxyInstance(
                FakeMessageDeliveryRepository.class.getClassLoader(),
                new Class<?>[]{MessageDeliveryRepository.class},
                (p, method, args) -> switch (method.getName()) {
                    case "save" -> save((MessageDeliveryEntity) args[0]);
                    case "flush" -> {
                        flushCount++;
                        yield null;
                    }
                    case "existsEligible" -> {
                        eligibleCalls.add(new EligibleCall((Instant) args[0], (Instant) args[1]));
                        yield eligible;
                    }
                    case "claimRunnable" -> {
                        claimCalls.add(new ClaimCall((Instant) args[0], (Instant) args[1],
                                (String) args[2], (int) args[3]));
                        yield claimResult;
                    }
                    case "updateStatus" -> {
                        statusUpdates.add(new StatusUpdate((UUID) args[0], (DeliveryStatus) args[1], (Instant) args[2]));
                        yield 1;
                    }
                    case "updateAttempt" -> {
                        attemptUpdates.add(new AttemptUpdate((UUID) args[0], (DeliveryStatus) args[1], (int) args[2],
                                (Instant) args[3], (String) args[4], (Instant) args[5]));
                        yield 1;
                    }
                    case "countByStatus" -> countsByStatus.getOrDefault((DeliveryStatus) args[0], 0L);
                    case "toString" -> "FakeMessageDeliveryRepository";
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    public MessageDeliveryRepository repository() {
        return proxy;
    }

    private MessageDeliveryEntity save(MessageDeliveryEntity entity) {
        RuntimeException failure = saveFailure.apply(entity);
        if (failure != null) {
            throw failure;
        }
        saved.add(entity);
        return entity;
    }
}
