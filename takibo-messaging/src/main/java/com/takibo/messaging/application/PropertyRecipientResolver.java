package com.takibo.messaging.application;

import com.takibo.messaging.config.MessagingProperties;
import com.takibo.messaging.domain.MessageAction;
import com.takibo.messaging.domain.Recipient;

import java.util.ArrayList;
import java.util.List;

public class PropertyRecipientResolver implements RecipientResolver {

    private final MessagingProperties properties;

    public PropertyRecipientResolver(MessagingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(String messageType) {
        return properties.getCatalog().getMessages().containsKey(messageType);
    }

    @Override
    public List<Recipient> resolve(MessageAction action, MessagingContext context) {
        MessagingProperties.MessageTemplate template = properties.getCatalog().getMessages().get(action.messageType());
        List<Recipient> recipients = new ArrayList<>();
        if (template == null || template.getRecipients() == null) {
            return recipients;
        }
        for (String email : template.getRecipients()) {
            if (email == null || email.isBlank()) {
                continue;
            }
            String key = "email:" + email.toLowerCase();
            recipients.add(new Recipient("EMAIL", email, key));
        }
        return recipients;
    }
}
