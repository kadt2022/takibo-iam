package com.takibo.messaging.application;

import com.takibo.messaging.config.MessagingProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesMessageCatalogTest {

    private final MessagingProperties properties = new MessagingProperties();
    private final PropertiesMessageCatalog catalog = new PropertiesMessageCatalog(properties);

    @Test
    void findsTemplateDeclaredInProperties() {
        MessagingProperties.MessageTemplate template = new MessagingProperties.MessageTemplate();
        properties.getCatalog().getMessages().put("WELCOME", template);

        Optional<MessagingProperties.MessageTemplate> found = catalog.findTemplate("WELCOME");

        assertTrue(found.isPresent());
        assertSame(template, found.get());
    }

    @Test
    void returnsEmptyForUnknownType() {
        assertTrue(catalog.findTemplate("UNKNOWN").isEmpty());
    }

    @Test
    void returnsEmptyForNullType() {
        assertTrue(catalog.findTemplate(null).isEmpty());
    }

    @Test
    void lookupIsCaseSensitive() {
        properties.getCatalog().getMessages().put("WELCOME", new MessagingProperties.MessageTemplate());

        assertTrue(catalog.findTemplate("welcome").isEmpty());
    }

    @Test
    void reflectsTemplatesAddedAfterConstruction() {
        assertTrue(catalog.findTemplate("LATE").isEmpty());

        properties.getCatalog().getMessages().put("LATE", new MessagingProperties.MessageTemplate());

        assertTrue(catalog.findTemplate("LATE").isPresent());
    }
}
