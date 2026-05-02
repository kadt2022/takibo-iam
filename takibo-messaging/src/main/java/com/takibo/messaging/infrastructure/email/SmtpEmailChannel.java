package com.takibo.messaging.infrastructure.email;

import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

public class SmtpEmailChannel implements MessageChannel {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailChannel.class);

    private final JavaMailSender mailSender;

    public SmtpEmailChannel(JavaMailSender mailSender) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public ChannelResult send(MessageDeliveryEntity delivery) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(delivery.getRecipientValue());
            if (delivery.getFromAddress() != null && !delivery.getFromAddress().isBlank()) {
                msg.setFrom(delivery.getFromAddress());
            }
            msg.setSubject(nullToEmpty(delivery.getSubject()));
            msg.setText(nullToEmpty(delivery.getBody()));

            mailSender.send(msg);

            log.info("Messaging delivery sent | id={} type={} to={}",
                    delivery.getId(), delivery.getMessageType(), delivery.getRecipientValue());

            return ChannelResult.sent();
        } catch (MailException e) {
            String error = trimError(e.getMessage());
            log.warn("Messaging delivery send failed | id={} type={} to={} error={}",
                    delivery.getId(), delivery.getMessageType(), delivery.getRecipientValue(), error);
            return new ChannelResult(DeliveryStatus.FAILED, error);
        } catch (Exception e) {
            String error = trimError(e.getMessage());
            log.error("Messaging delivery send error | id={} type={} to={}",
                    delivery.getId(), delivery.getMessageType(), delivery.getRecipientValue(), e);
            return new ChannelResult(DeliveryStatus.FAILED, error);
        }
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String trimError(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
