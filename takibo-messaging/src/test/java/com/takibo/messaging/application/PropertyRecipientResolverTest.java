package com.takibo.messaging.application;

import com.takibo.messaging.config.MessagingProperties;
import com.takibo.messaging.domain.MessageAction;
import com.takibo.messaging.domain.Recipient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyRecipientResolverTest {

    private final MessagingProperties properties = new MessagingProperties();
    private final PropertyRecipientResolver resolver = new PropertyRecipientResolver(properties);

    @Test
    void supportsOnlyMessageTypesDeclaredInCatalog() {
        properties.getCatalog().getMessages().put("ALERT", new MessagingProperties.MessageTemplate());

        assertTrue(resolver.supports("ALERT"));
        assertFalse(resolver.supports("OTHER"));
        assertFalse(resolver.supports(null));
    }

    @Test
    void resolvesConfiguredEmailsWithLowercasedKeyAndOriginalValue() {
        register("ALERT", List.of("Ops@Takibo.test", "security@takibo.test"));

        List<Recipient> recipients = resolver.resolve(action("ALERT"), MessagingContext.none());

        assertEquals(List.of(
                new Recipient("EMAIL", "Ops@Takibo.test", "email:ops@takibo.test"),
                new Recipient("EMAIL", "security@takibo.test", "email:security@takibo.test")
        ), recipients);
    }

    @Test
    void skipsNullAndBlankEmails() {
        register("ALERT", Arrays.asList(null, "", "   ", "ops@takibo.test"));

        List<Recipient> recipients = resolver.resolve(action("ALERT"), MessagingContext.none());

        assertEquals(List.of(new Recipient("EMAIL", "ops@takibo.test", "email:ops@takibo.test")), recipients);
    }

    @Test
    void returnsEmptyListWhenTemplateHasNoRecipients() {
        register("ALERT", null);

        assertTrue(resolver.resolve(action("ALERT"), MessagingContext.none()).isEmpty());
    }

    @Test
    void returnsEmptyListWhenTemplateDefaultsAreUsed() {
        properties.getCatalog().getMessages().put("ALERT", new MessagingProperties.MessageTemplate());

        assertTrue(resolver.resolve(action("ALERT"), MessagingContext.none()).isEmpty());
    }

    @Test
    void returnsEmptyListForUnknownMessageType() {
        assertTrue(resolver.resolve(action("UNKNOWN"), MessagingContext.none()).isEmpty());
    }

    @Test
    void returnedListIsMutableCopyNotBackedByConfiguration() {
        register("ALERT", List.of("ops@takibo.test"));

        List<Recipient> recipients = resolver.resolve(action("ALERT"), MessagingContext.none());
        recipients.clear();

        assertEquals(1, resolver.resolve(action("ALERT"), MessagingContext.none()).size());
    }

    private void register(String type, List<String> recipients) {
        MessagingProperties.MessageTemplate template = new MessagingProperties.MessageTemplate();
        template.setRecipients(recipients);
        properties.getCatalog().getMessages().put(type, template);
    }

    private static MessageAction action(String type) {
        return MessageAction.builder(type).dedupKey("D").build();
    }
}
