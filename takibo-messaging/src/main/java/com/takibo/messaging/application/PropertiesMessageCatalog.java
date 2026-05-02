package com.takibo.messaging.application;

import com.takibo.messaging.config.MessagingProperties;

import java.util.Optional;

public class PropertiesMessageCatalog implements MessageCatalog {

    private final MessagingProperties properties;

    public PropertiesMessageCatalog(MessagingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<MessagingProperties.MessageTemplate> findTemplate(String messageType) {
        if (messageType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(properties.getCatalog().getMessages().get(messageType));
    }
}
