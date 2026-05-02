package com.takibo.messaging.config;

import com.takibo.messaging.domain.ChannelType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "takibo.messaging")
public class MessagingProperties {

    private boolean enabled = true;
    private final Processor processor = new Processor();
    private final Backoff backoff = new Backoff();
    private final Catalog catalog = new Catalog();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Processor getProcessor() {
        return processor;
    }

    public Backoff getBackoff() {
        return backoff;
    }

    public Catalog getCatalog() {
        return catalog;
    }

    public static class Processor {
        private boolean enabled = true;
        private Duration fixedDelay = Duration.ofSeconds(2);
        private int batchSize = 50;
        private int maxAttempts = 10;
        private Duration lockTimeout = Duration.ofSeconds(30);
        private String lockedBy = "takibo-messaging";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(Duration fixedDelay) {
            this.fixedDelay = fixedDelay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }

        public String getLockedBy() {
            return lockedBy;
        }

        public void setLockedBy(String lockedBy) {
            this.lockedBy = lockedBy;
        }
    }

    public static class Backoff {
        private String type = "exponential";
        private Duration baseDelay = Duration.ofSeconds(5);
        private Duration maxDelay = Duration.ofMinutes(10);
        private int multiplier = 2;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Duration getBaseDelay() {
            return baseDelay;
        }

        public void setBaseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        public int getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(int multiplier) {
            this.multiplier = multiplier;
        }
    }

    public static class Catalog {
        private final Map<String, MessageTemplate> messages = new LinkedHashMap<>();

        public Map<String, MessageTemplate> getMessages() {
            return messages;
        }
    }

    public static class MessageTemplate {
        private ChannelType channel = ChannelType.EMAIL;
        private String from;
        private String subject;
        private String body;
        private List<String> recipients = List.of();

        public ChannelType getChannel() {
            return channel;
        }

        public void setChannel(ChannelType channel) {
            this.channel = channel;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = recipients;
        }
    }
}
