package com.takibo.messaging.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageActionTest {

    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void buildsActionWithAllFields() {
        MessageAction action = MessageAction.builder("WELCOME_SPACE")
                .orgId(ORG)
                .spaceId(SPACE)
                .dedupKey("MSG:WELCOME_SPACE:1")
                .channelOverride(ChannelType.WEBHOOK)
                .attribute("name", "Alice")
                .build();

        assertEquals("WELCOME_SPACE", action.messageType());
        assertEquals(ORG, action.orgId());
        assertEquals(SPACE, action.spaceId());
        assertEquals("MSG:WELCOME_SPACE:1", action.dedupKey());
        assertEquals(ChannelType.WEBHOOK, action.channelOverride());
        assertEquals(Map.of("name", "Alice"), action.attributes());
    }

    @Test
    void optionalFieldsDefaultToNullOrEmpty() {
        MessageAction action = MessageAction.builder("T").dedupKey("D").build();

        assertNull(action.orgId());
        assertNull(action.spaceId());
        assertNull(action.channelOverride());
        assertTrue(action.attributes().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingMessageType(String messageType) {
        MessageAction.Builder builder = MessageAction.builder(messageType).dedupKey("D");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertEquals("messageType is required", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"\t"})
    void rejectsMissingDedupKey(String dedupKey) {
        MessageAction.Builder builder = MessageAction.builder("T").dedupKey(dedupKey);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertEquals("dedupKey is required", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingAttributeKey(String key) {
        MessageAction.Builder builder = MessageAction.builder("T");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> builder.attribute(key, "v"));
        assertEquals("attribute key is required", ex.getMessage());
    }

    @Test
    void ignoresNullAttributeValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("kept", 1);
        values.put("dropped", null);

        MessageAction action = MessageAction.builder("T").dedupKey("D")
                .attribute("alsoDropped", null)
                .attributes(values)
                .build();

        assertEquals(Map.of("kept", 1), action.attributes());
    }

    @Test
    void acceptsNullAttributesMap() {
        MessageAction action = MessageAction.builder("T").dedupKey("D").attributes(null).build();

        assertTrue(action.attributes().isEmpty());
    }

    @Test
    void attributesMapRejectsBlankKeys() {
        Map<String, Object> values = Map.of(" ", "v");
        MessageAction.Builder builder = MessageAction.builder("T");

        assertThrows(IllegalArgumentException.class, () -> builder.attributes(values));
    }

    @Test
    void preservesAttributeInsertionOrder() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("z", 1);
        values.put("a", 2);
        values.put("m", 3);

        MessageAction action = MessageAction.builder("T").dedupKey("D").attributes(values).build();

        assertEquals(List.of("z", "a", "m"), List.copyOf(action.attributes().keySet()));
    }

    @Test
    void attributesAreImmutable() {
        Map<String, Object> attributes =
                MessageAction.builder("T").dedupKey("D").attribute("k", "v").build().attributes();

        assertThrows(UnsupportedOperationException.class, () -> attributes.put("x", "y"));
    }

    @Test
    void builtActionIsIsolatedFromLaterBuilderChanges() {
        MessageAction.Builder builder = MessageAction.builder("T").dedupKey("D").attribute("k", "v");
        MessageAction first = builder.build();

        builder.attribute("later", "x");

        assertEquals(Map.of("k", "v"), first.attributes());
    }

    @Test
    void equalActionsHaveSameHashCode() {
        MessageAction a = sample().build();
        MessageAction b = sample().build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
    }

    @Test
    void actionsDifferingByAnyFieldAreNotEqual() {
        MessageAction reference = sample().build();

        assertNotEquals(reference, MessageAction.builder("OTHER").orgId(ORG).spaceId(SPACE).dedupKey("D")
                .channelOverride(ChannelType.EMAIL).attribute("k", "v").build());
        assertNotEquals(reference, sample().orgId(UUID.randomUUID()).build());
        assertNotEquals(reference, sample().spaceId(null).build());
        assertNotEquals(reference, sample().dedupKey("D2").build());
        assertNotEquals(reference, sample().channelOverride(ChannelType.IN_APP).build());
        assertNotEquals(reference, sample().attribute("k", "other").build());
        assertNotEquals(reference, "not an action");
        assertNotEquals(reference, null);
    }

    @Test
    void toStringDoesNotExposeAttributeValues() {
        MessageAction action = MessageAction.builder("RESET_PASSWORD")
                .orgId(ORG)
                .dedupKey("D")
                .attribute("token", "s3cr3t-reset-token")
                .build();

        String text = action.toString();

        assertTrue(text.contains("RESET_PASSWORD"));
        assertTrue(text.contains("D"));
        assertTrue(text.contains(ORG.toString()));
        assertFalse(text.contains("s3cr3t-reset-token"), "les attributs (potentiellement sensibles) ne doivent pas etre journalises");
    }

    private static MessageAction.Builder sample() {
        return MessageAction.builder("T")
                .orgId(ORG)
                .spaceId(SPACE)
                .dedupKey("D")
                .channelOverride(ChannelType.EMAIL)
                .attribute("k", "v");
    }
}
