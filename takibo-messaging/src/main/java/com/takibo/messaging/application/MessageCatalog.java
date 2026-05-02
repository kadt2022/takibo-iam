package com.takibo.messaging.application;

import com.takibo.messaging.config.MessagingProperties;

import java.util.Optional;

public interface MessageCatalog {

    Optional<MessagingProperties.MessageTemplate> findTemplate(String messageType);
}
