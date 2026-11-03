package com.takibo.messaging.infrastructure.email;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpEmailChannelTest {

    @Test
    void rejectsNullMailSender() {
        assertThrows(NullPointerException.class, () -> new SmtpEmailChannel(null));
    }

    @Test
    void isEmailChannel() {
        assertEquals(ChannelType.EMAIL, new SmtpEmailChannel(new CapturingMailSender()).channelType());
    }

    @Test
    void deliversEmailOverSmtp() throws Exception {
        GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        greenMail.start();
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(greenMail.getSmtp().getPort());

            MessageChannel.ChannelResult result = new SmtpEmailChannel(sender).send(delivery());

            assertEquals(DeliveryStatus.SENT, result.status());
            assertNull(result.error());
            assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
            MimeMessage received = greenMail.getReceivedMessages()[0];
            assertEquals("Welcome", received.getSubject());
            assertEquals("no-reply@takibo.test", received.getFrom()[0].toString());
            assertEquals("test@takibo.test", received.getAllRecipients()[0].toString());
            assertEquals("Hello from Takibo", received.getContent().toString().trim());
        } finally {
            greenMail.stop();
        }
    }

    @Test
    void mapsDeliveryFieldsToMailMessage() {
        CapturingMailSender sender = new CapturingMailSender();

        MessageChannel.ChannelResult result = new SmtpEmailChannel(sender).send(delivery());

        assertEquals(DeliveryStatus.SENT, result.status());
        SimpleMailMessage message = sender.only();
        assertArrayEquals(new String[]{"test@takibo.test"}, message.getTo());
        assertEquals("no-reply@takibo.test", message.getFrom());
        assertEquals("Welcome", message.getSubject());
        assertEquals("Hello from Takibo", message.getText());
    }

    @Test
    void leavesSenderUnsetWhenFromAddressIsBlankOrMissing() {
        CapturingMailSender sender = new CapturingMailSender();
        SmtpEmailChannel channel = new SmtpEmailChannel(sender);

        MessageDeliveryEntity blankFrom = delivery();
        blankFrom.setFromAddress("  ");
        channel.send(blankFrom);
        MessageDeliveryEntity noFrom = delivery();
        noFrom.setFromAddress(null);
        channel.send(noFrom);

        assertNull(sender.messages.get(0).getFrom(), "laisse le serveur SMTP appliquer l'expediteur par defaut");
        assertNull(sender.messages.get(1).getFrom());
    }

    @Test
    void sendsEmptySubjectAndBodyInsteadOfNull() {
        CapturingMailSender sender = new CapturingMailSender();
        MessageDeliveryEntity delivery = delivery();
        delivery.setSubject(null);
        delivery.setBody(null);

        new SmtpEmailChannel(sender).send(delivery);

        assertEquals("", sender.only().getSubject());
        assertEquals("", sender.only().getText());
    }

    @Test
    void reportsRetryableFailureWhenSmtpRejectsMessage() {
        CapturingMailSender sender = new CapturingMailSender();
        sender.failure = new MailSendException("550 relay denied");

        MessageChannel.ChannelResult result = new SmtpEmailChannel(sender).send(delivery());

        assertEquals(DeliveryStatus.FAILED, result.status());
        assertEquals("550 relay denied", result.error());
    }

    @Test
    void truncatesLongErrorMessagesTo500Characters() {
        CapturingMailSender sender = new CapturingMailSender();
        sender.failure = new MailSendException("e".repeat(1200));

        MessageChannel.ChannelResult result = new SmtpEmailChannel(sender).send(delivery());

        assertEquals("e".repeat(500), result.error());
    }

    @Test
    void reportsUnknownErrorWhenExceptionHasNoMessage() {
        CapturingMailSender sender = new CapturingMailSender();
        sender.failure = new MailSendException((String) null);

        assertEquals("unknown", new SmtpEmailChannel(sender).send(delivery()).error());
    }

    @Test
    void unexpectedExceptionsAreContainedAsFailure() {
        CapturingMailSender sender = new CapturingMailSender();
        sender.failure = new IllegalStateException("mail session broken");

        MessageChannel.ChannelResult result = new SmtpEmailChannel(sender).send(delivery());

        assertEquals(DeliveryStatus.FAILED, result.status());
        assertEquals("mail session broken", result.error());
    }

    private static MessageDeliveryEntity delivery() {
        MessageDeliveryEntity delivery = new MessageDeliveryEntity();
        delivery.setId(UUID.randomUUID());
        delivery.setMessageType("WELCOME_SPACE");
        delivery.setChannel("EMAIL");
        delivery.setRecipientType("EMAIL");
        delivery.setRecipientValue("test@takibo.test");
        delivery.setRecipientKey("email:test@takibo.test");
        delivery.setFromAddress("no-reply@takibo.test");
        delivery.setSubject("Welcome");
        delivery.setBody("Hello from Takibo");
        return delivery;
    }

    /** Expediteur qui capture les messages au lieu de les envoyer, et peut simuler une panne. */
    private static final class CapturingMailSender extends JavaMailSenderImpl {
        private final List<SimpleMailMessage> messages = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            if (failure != null) {
                throw failure;
            }
            messages.addAll(List.of(simpleMessages));
        }

        SimpleMailMessage only() {
            assertEquals(1, messages.size());
            return messages.get(0);
        }
    }
}
