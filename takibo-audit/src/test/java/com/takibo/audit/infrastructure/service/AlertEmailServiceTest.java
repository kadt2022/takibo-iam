package com.takibo.audit.infrastructure.service;

import com.takibo.audit.domain.AuditType;
import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.messaging.application.MessagingContext;
import com.takibo.messaging.application.MessagingDispatcher;
import com.takibo.messaging.domain.MessageAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Clé de déduplication des alertes de sécurité : une par alerte. Les alertes de
 * {@code AlertTriggerAspect} ne sont jamais enregistrées et n'ont donc pas d'identifiant ; elles
 * ne doivent pas partager une même clé, sinon seule la première atteint chaque destinataire.
 */
class AlertEmailServiceTest {

    private static final String PREFIX = "MSG:AUDIT_SECURITY_ALERT:";

    private final MessagingDispatcher dispatcher = mock(MessagingDispatcher.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<MessagingDispatcher> provider = mock(ObjectProvider.class);

    private final AlertEmailService service = new AlertEmailService(provider);

    private static AuditEvent alert(Long id) {
        return AuditEvent.builder()
                .id(id)
                .auditType(AuditType.MEDIUM)
                .status("FAILED")
                .entityType("signup")
                .orgId("aaaaaaaa-0000-0000-0000-000000000001")
                .build();
    }

    private List<MessageAction> dispatchedActions(int expected) {
        ArgumentCaptor<MessageAction> actions = ArgumentCaptor.forClass(MessageAction.class);
        verify(dispatcher, times(expected)).dispatch(actions.capture(), any(MessagingContext.class));
        return actions.getAllValues();
    }

    @Test
    void persistedAuditEvent_isDeduplicatedOnItsIdentifier() {
        when(provider.getIfAvailable()).thenReturn(dispatcher);

        service.sendAlert(alert(928L));

        assertThat(dispatchedActions(1).get(0).dedupKey()).isEqualTo(PREFIX + "928");
    }

    @Test
    void unsavedAlerts_eachGetTheirOwnKey_soNoneIsDroppedAsADuplicate() {
        when(provider.getIfAvailable()).thenReturn(dispatcher);

        service.sendAlert(alert(null));
        service.sendAlert(alert(null));

        List<String> keys = dispatchedActions(2).stream().map(MessageAction::dedupKey).toList();
        assertThat(keys).doesNotHaveDuplicates()
                .allSatisfy(key -> assertThat(key).startsWith(PREFIX + "unsaved:").doesNotContain("null"));
    }

    @Test
    void unsavedAlertKeys_neverCollideWithPersistedEventKeys() {
        when(provider.getIfAvailable()).thenReturn(dispatcher);

        service.sendAlert(alert(1L));
        service.sendAlert(alert(null));

        List<String> keys = dispatchedActions(2).stream().map(MessageAction::dedupKey).toList();
        assertThat(keys.get(0)).isEqualTo(PREFIX + "1");
        assertThat(keys.get(1)).startsWith(PREFIX + "unsaved:");
    }

    @Test
    void alertIsSkipped_whenMessagingIsNotAvailable() {
        when(provider.getIfAvailable()).thenReturn(null);

        service.sendAlert(alert(null));

        verifyNoInteractions(dispatcher);
    }
}
