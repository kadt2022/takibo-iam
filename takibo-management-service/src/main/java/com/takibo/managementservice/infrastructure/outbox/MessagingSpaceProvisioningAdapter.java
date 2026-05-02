package com.takibo.managementservice.infrastructure.outbox;

import com.takibo.messaging.application.MessagingContext;
import com.takibo.messaging.application.MessagingDispatcher;
import com.takibo.messaging.domain.MessageAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "takibo.space.provisioning", name = "mode", havingValue = "messaging")
public class MessagingSpaceProvisioningAdapter implements SpaceProvisioningPort {

    private final MessagingDispatcher dispatcher;

    public MessagingSpaceProvisioningAdapter(MessagingDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void onSpaceCreated(UUID orgId, UUID spaceId, UUID ownerAccountId, String source, UUID outboxEventId) {

        MessageAction action = MessageAction.builder("WELCOME_SPACE")
                .orgId(orgId)
                .spaceId(spaceId)
                .dedupKey("MSG:WELCOME_SPACE:" + spaceId + ":account:" + ownerAccountId)
                .attribute("orgId", String.valueOf(orgId))
                .attribute("spaceId", String.valueOf(spaceId))
                .attribute("ownerAccountId", String.valueOf(ownerAccountId))
                .attribute("spaceCode", String.valueOf(spaceId))
                .attribute("spaceName", "Takibo Space")
                .build();

        MessagingContext ctx = MessagingContext.builder()
                .correlationOutboxId(outboxEventId)
                .traceId(null)
                .build();

        dispatcher.dispatch(action, ctx);

    }
}
