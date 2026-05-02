package com.takibo.messaging;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.takibo.messaging.infrastructure.email.SmtpEmailChannel;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import com.takibo.messaging.domain.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpEmailChannelTest {

//    @Test
//    void sendEmailOverSmtp() throws Exception {
//        GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);
//        greenMail.start();
//        try {
//            JavaMailSenderImpl sender = new JavaMailSenderImpl();
//            sender.setHost("smtp.gmail.com");
//            sender.setPort(greenMail.getSmtp().getPort());
//
//            SmtpEmailChannel channel = new SmtpEmailChannel(sender);
//
//            MessageDeliveryEntity delivery = new MessageDeliveryEntity();
//            delivery.setId(UUID.randomUUID());
//            delivery.setMessageType("WELCOME_SPACE");
//            delivery.setChannel("EMAIL");
//            delivery.setRecipientType("EMAIL");
//            delivery.setRecipientValue("test@takibo.test");
//            delivery.setRecipientKey("email:test@takibo.test");
//            delivery.setFromAddress("no-reply@takibo.test");
//            delivery.setSubject("Welcome");
//            delivery.setBody("Hello from Takibo");
//            delivery.setStatus(DeliveryStatus.PENDING);
//            delivery.setAttempts(0);
//            delivery.setNextRunAt(Instant.now());
//            delivery.setDedupKey("MSG:WELCOME_SPACE:1:email:test@takibo.test");
//            delivery.setCreatedAt(Instant.now());
//            delivery.setUpdatedAt(Instant.now());
//
//            var result = channel.send(delivery);
//
//            assertEquals(DeliveryStatus.SENT, result.status());
//            assertTrue(greenMail.waitForIncomingEmail(1));
//        } finally {
//            greenMail.stop();
//        }
//    }
}
