package com.takibo.messaging.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.messaging.config.MessagingProperties;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.domain.MessageAction;
import com.takibo.messaging.domain.Recipient;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-01-06T10:15:30Z");
    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FakeMessageDeliveryRepository fakeRepository = new FakeMessageDeliveryRepository();
    private final MessagingProperties.MessageTemplate template = template();

    // --- Detection du conflit de deduplication (tests historiques) ---

    @Test
    void detectsMessageDeliveryDedupConstraintByName() throws Exception {
        assertTrue(isDedupConflict("duplicate key value violates constraint \"UQ_MESSAGE_DELIVERIES_DEDUP_KEY\""));
    }

    @Test
    void detectsMessageDeliveryDedupConstraintByTableAndColumn() throws Exception {
        assertTrue(isDedupConflict("duplicate key on message_deliveries for dedup_key"));
    }

    @Test
    void ignoresUnrelatedConstraint() throws Exception {
        assertFalse(isDedupConflict("duplicate key value violates constraint \"other_key\""));
    }

    // --- Garde-fous ---

    @Test
    void rejectsNullActionOrContext() {
        MessagingDispatcher dispatcher = dispatcher(List.of(fixedResolver()));
        MessageAction action = action().build();

        assertThrows(NullPointerException.class, () -> dispatcher.dispatch(null, MessagingContext.none()));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatch(action, null));
    }

    @Test
    void failsWhenNoTemplateIsConfiguredForType() {
        MessagingDispatcher dispatcher = new MessagingDispatcher(fakeRepository.repository(), List.of(fixedResolver()),
                Map.of(), type -> Optional.empty(), new TemplateEngine(), new ObjectMapper(), fixedClock());
        MessageAction action = action().build();
        MessagingContext context = MessagingContext.none();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch(action, context));

        assertEquals("No message template configured for type: WELCOME_SPACE", ex.getMessage());
        assertTrue(fakeRepository.saved.isEmpty());
    }

    // --- Resolution des destinataires ---

    @Test
    void skipsDispatchWhenNoResolverSupportsType() {
        RecipientResolver unsupported = resolver(false, List.of(alice()));

        MessagingDispatcher.DispatchResult result = dispatcher(List.of(unsupported))
                .dispatch(action().build(), MessagingContext.none());

        assertEquals(new MessagingDispatcher.DispatchResult(0, 0), result);
        assertTrue(fakeRepository.saved.isEmpty());
    }

    @Test
    void skipsDispatchWhenResolverReturnsNoRecipients() {
        MessagingDispatcher.DispatchResult result = dispatcher(List.of(resolver(true, List.of())))
                .dispatch(action().build(), MessagingContext.none());

        assertEquals(new MessagingDispatcher.DispatchResult(0, 0), result);
        assertTrue(fakeRepository.saved.isEmpty());
    }

    @Test
    void usesOnlyFirstResolverSupportingType() {
        List<String> calls = new ArrayList<>();
        RecipientResolver skipped = new RecordingResolver("skipped", false, List.of(bob()), calls);
        RecipientResolver first = new RecordingResolver("first", true, List.of(alice()), calls);
        RecipientResolver second = new RecordingResolver("second", true, List.of(bob()), calls);

        dispatcher(List.of(skipped, first, second)).dispatch(action().build(), MessagingContext.none());

        assertEquals(List.of("supports:skipped", "supports:first", "resolve:first"), calls);
        assertEquals(1, fakeRepository.saved.size());
        assertEquals("a@takibo.test", fakeRepository.saved.get(0).getRecipientValue());
    }

    // --- Construction de la livraison ---

    @Test
    void persistsOnePendingDeliveryPerRecipientWithRenderedContent() {
        UUID outboxId = UUID.randomUUID();
        MessagingContext context = MessagingContext.builder().correlationOutboxId(outboxId).traceId("trace-1").build();

        MessagingDispatcher.DispatchResult result = dispatcher(List.of(resolver(true, List.of(alice(), bob()))))
                .dispatch(action().build(), context);

        assertEquals(new MessagingDispatcher.DispatchResult(2, 0), result);
        assertEquals(2, fakeRepository.saved.size());
        assertEquals(2, fakeRepository.flushCount, "chaque livraison est flushee pour detecter le doublon immediatement");

        MessageDeliveryEntity first = fakeRepository.saved.get(0);
        assertNotNull(first.getId());
        assertEquals(ORG, first.getOrgId());
        assertEquals(SPACE, first.getSpaceId());
        assertEquals("WELCOME_SPACE", first.getMessageType());
        assertEquals("EMAIL", first.getChannel());
        assertEquals("EMAIL", first.getRecipientType());
        assertEquals("a@takibo.test", first.getRecipientValue());
        assertEquals("email:a@takibo.test", first.getRecipientKey());
        assertEquals("no-reply@takibo.test", first.getFromAddress());
        assertEquals("Welcome Alice", first.getSubject());
        assertEquals("Hello Alice, join Ops", first.getBody());
        assertEquals("{\"name\":\"Alice\",\"space\":\"Ops\"}", first.getPayloadJson());
        assertEquals(DeliveryStatus.PENDING, first.getStatus());
        assertEquals(0, first.getAttempts());
        assertEquals(NOW, first.getNextRunAt());
        assertNull(first.getLastError());
        assertNull(first.getLockedAt());
        assertNull(first.getLockedBy());
        assertEquals("MSG:WELCOME_SPACE:1:email:a@takibo.test", first.getDedupKey());
        assertEquals(outboxId, first.getCorrelationOutboxId());
        assertEquals("trace-1", first.getTraceId());
        assertEquals(NOW, first.getCreatedAt());
        assertEquals(NOW, first.getUpdatedAt());

        MessageDeliveryEntity second = fakeRepository.saved.get(1);
        assertEquals("MSG:WELCOME_SPACE:1:email:b@takibo.test", second.getDedupKey());
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void usesTemplateChannelWhenNoOverride() {
        template.setChannel(ChannelType.IN_APP);

        dispatcher(List.of(fixedResolver())).dispatch(action().build(), MessagingContext.none());

        assertEquals("IN_APP", fakeRepository.saved.get(0).getChannel());
    }

    @Test
    void channelOverrideTakesPrecedenceOverTemplateChannel() {
        dispatcher(List.of(fixedResolver()))
                .dispatch(action().channelOverride(ChannelType.WEBHOOK).build(), MessagingContext.none());

        assertEquals("WEBHOOK", fakeRepository.saved.get(0).getChannel());
    }

    @Test
    void emptyContextLeavesCorrelationAndTraceEmpty() {
        dispatcher(List.of(fixedResolver())).dispatch(action().build(), MessagingContext.none());

        MessageDeliveryEntity delivery = fakeRepository.saved.get(0);
        assertNull(delivery.getCorrelationOutboxId());
        assertNull(delivery.getTraceId());
    }

    @Test
    void storesNullPayloadWhenAttributesCannotBeSerialized() {
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("boom") { };
            }
        };
        MessagingDispatcher dispatcher = new MessagingDispatcher(fakeRepository.repository(), List.of(fixedResolver()),
                Map.of(), type -> Optional.of(template), new TemplateEngine(), failingMapper, fixedClock());

        MessagingDispatcher.DispatchResult result = dispatcher.dispatch(action().build(), MessagingContext.none());

        assertEquals(1, result.created());
        assertNull(fakeRepository.saved.get(0).getPayloadJson());
    }

    // --- Idempotence ---

    @Test
    void countsDuplicateDeliveriesAsSkippedAndContinues() {
        fakeRepository.saveFailure = entity -> entity.getRecipientValue().equals("a@takibo.test")
                ? new DataIntegrityViolationException("dup",
                new RuntimeException("duplicate key value violates unique constraint \"uq_message_deliveries_dedup_key\""))
                : null;

        MessagingDispatcher.DispatchResult result = dispatcher(List.of(resolver(true, List.of(alice(), bob()))))
                .dispatch(action().build(), MessagingContext.none());

        assertEquals(new MessagingDispatcher.DispatchResult(1, 1), result);
        assertEquals(1, fakeRepository.saved.size());
        assertEquals("b@takibo.test", fakeRepository.saved.get(0).getRecipientValue());
    }

    @Test
    void rethrowsIntegrityViolationsUnrelatedToDeduplication() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("bad",
                new RuntimeException("null value in column \"recipient_key\" violates not-null constraint"));
        fakeRepository.saveFailure = entity -> failure;
        MessagingDispatcher dispatcher = dispatcher(List.of(fixedResolver()));
        MessageAction action = action().build();
        MessagingContext context = MessagingContext.none();

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> dispatcher.dispatch(action, context));

        assertSame(failure, thrown);
    }

    @Test
    void rethrowsIntegrityViolationWithoutMessage() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("x", new RuntimeException((String) null));
        fakeRepository.saveFailure = entity -> failure;
        MessagingDispatcher dispatcher = dispatcher(List.of(fixedResolver()));
        MessageAction action = action().build();
        MessagingContext context = MessagingContext.none();

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> dispatcher.dispatch(action, context)));
    }

    @Test
    void columnNameAloneIsNotEnoughToDetectDedupConflict() throws Exception {
        assertFalse(isDedupConflict("duplicate key on other_table for dedup_key"));
    }

    // --- Helpers ---

    private MessagingDispatcher dispatcher(List<RecipientResolver> resolvers) {
        return new MessagingDispatcher(fakeRepository.repository(), resolvers, Map.of(),
                type -> "WELCOME_SPACE".equals(type) ? Optional.of(template) : Optional.empty(),
                new TemplateEngine(), new ObjectMapper(), fixedClock());
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static MessageAction.Builder action() {
        return MessageAction.builder("WELCOME_SPACE")
                .orgId(ORG)
                .spaceId(SPACE)
                .dedupKey("MSG:WELCOME_SPACE:1")
                .attribute("name", "Alice")
                .attribute("space", "Ops");
    }

    private static MessagingProperties.MessageTemplate template() {
        MessagingProperties.MessageTemplate t = new MessagingProperties.MessageTemplate();
        t.setFrom("no-reply@takibo.test");
        t.setSubject("Welcome ${name}");
        t.setBody("Hello ${name}, join ${space}");
        return t;
    }

    private static Recipient alice() {
        return new Recipient("EMAIL", "a@takibo.test", "email:a@takibo.test");
    }

    private static Recipient bob() {
        return new Recipient("EMAIL", "b@takibo.test", "email:b@takibo.test");
    }

    private static RecipientResolver fixedResolver() {
        return resolver(true, List.of(alice()));
    }

    private static RecipientResolver resolver(boolean supports, List<Recipient> recipients) {
        return new RecordingResolver("r", supports, recipients, new ArrayList<>());
    }

    private record RecordingResolver(String name, boolean supported, List<Recipient> recipients, List<String> calls)
            implements RecipientResolver {

        @Override
        public boolean supports(String messageType) {
            calls.add("supports:" + name);
            return supported;
        }

        @Override
        public List<Recipient> resolve(MessageAction action, MessagingContext context) {
            calls.add("resolve:" + name);
            return recipients;
        }
    }

    private boolean isDedupConflict(String message) throws Exception {
        MessagingDispatcher dispatcher = dispatcher(List.of());
        Method method = MessagingDispatcher.class.getDeclaredMethod("isDedupConflict", DataIntegrityViolationException.class);
        method.setAccessible(true);
        DataIntegrityViolationException exception = new DataIntegrityViolationException("dispatch failed", new RuntimeException(message));
        return (boolean) method.invoke(dispatcher, exception);
    }
}
