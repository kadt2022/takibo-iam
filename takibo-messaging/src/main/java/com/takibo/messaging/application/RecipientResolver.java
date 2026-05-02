package com.takibo.messaging.application;

import com.takibo.messaging.domain.MessageAction;
import com.takibo.messaging.domain.Recipient;

import java.util.List;

public interface RecipientResolver {

    boolean supports(String messageType);

    List<Recipient> resolve(MessageAction action, MessagingContext context);
}
