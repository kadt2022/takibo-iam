package com.takibo.managementservice.infrastructure.outbox;

import com.takibo.messaging.application.MessagingContext;
import com.takibo.messaging.application.MessagingDispatcher;
import com.takibo.messaging.domain.MessageAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MessagingSpaceProvisioningAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OWNER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID OUTBOX_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private final MessagingDispatcher dispatcher = mock(MessagingDispatcher.class);
    private final MessagingSpaceProvisioningAdapter adapter = new MessagingSpaceProvisioningAdapter(dispatcher);

    @Test
    void sendsTheWelcomeMessageToTheOwnerInsideTheSpaceBoundary() {
        adapter.onSpaceCreated(ORG_ID, SPACE_ID, OWNER_ID, "HUMAN", OUTBOX_ID);

        ArgumentCaptor<MessageAction> action = ArgumentCaptor.forClass(MessageAction.class);
        ArgumentCaptor<MessagingContext> context = ArgumentCaptor.forClass(MessagingContext.class);
        verify(dispatcher).dispatch(action.capture(), context.capture());

        assertThat(action.getValue().messageType()).isEqualTo("WELCOME_SPACE");
        assertThat(action.getValue().orgId()).isEqualTo(ORG_ID);
        assertThat(action.getValue().spaceId()).isEqualTo(SPACE_ID);
        assertThat(action.getValue().attributes())
                .containsEntry("orgId", ORG_ID.toString())
                .containsEntry("spaceId", SPACE_ID.toString())
                .containsEntry("ownerAccountId", OWNER_ID.toString());
        assertThat(context.getValue().correlationOutboxId()).isEqualTo(OUTBOX_ID);
        assertThat(context.getValue().traceId()).isNull();
    }

    @Test
    void aRedeliveredOutboxEventProducesTheSameDeduplicationKey() {
        adapter.onSpaceCreated(ORG_ID, SPACE_ID, OWNER_ID, "HUMAN", OUTBOX_ID);
        adapter.onSpaceCreated(ORG_ID, SPACE_ID, OWNER_ID, "SYSTEM", UUID.randomUUID());

        ArgumentCaptor<MessageAction> actions = ArgumentCaptor.forClass(MessageAction.class);
        verify(dispatcher, times(2)).dispatch(actions.capture(), org.mockito.ArgumentMatchers.any());

        assertThat(actions.getAllValues())
                .extracting(MessageAction::dedupKey)
                .containsOnly("MSG:WELCOME_SPACE:" + SPACE_ID + ":account:" + OWNER_ID);
    }

    @Test
    void anotherOwnerOfTheSameSpaceGetsItsOwnWelcome() {
        UUID otherOwner = UUID.randomUUID();
        adapter.onSpaceCreated(ORG_ID, SPACE_ID, OWNER_ID, "HUMAN", OUTBOX_ID);
        adapter.onSpaceCreated(ORG_ID, SPACE_ID, otherOwner, "HUMAN", OUTBOX_ID);

        ArgumentCaptor<MessageAction> actions = ArgumentCaptor.forClass(MessageAction.class);
        verify(dispatcher, times(2)).dispatch(actions.capture(), org.mockito.ArgumentMatchers.any());

        assertThat(actions.getAllValues()).extracting(MessageAction::dedupKey).doesNotHaveDuplicates();
    }
}
