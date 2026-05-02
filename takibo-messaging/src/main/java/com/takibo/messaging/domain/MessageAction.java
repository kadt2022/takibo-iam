package com.takibo.messaging.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MessageAction {

    private final String messageType;
    private final UUID orgId;
    private final UUID spaceId;
    private final String dedupKey;
    private final ChannelType channelOverride;
    private final Map<String, Object> attributes;

    private MessageAction(Builder b) {
        this.messageType = require(b.messageType, "messageType");
        this.orgId = b.orgId;
        this.spaceId = b.spaceId;
        this.dedupKey = require(b.dedupKey, "dedupKey");
        this.channelOverride = b.channelOverride;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(b.attributes));
    }

    public String messageType() {
        return messageType;
    }

    public UUID orgId() {
        return orgId;
    }

    public UUID spaceId() {
        return spaceId;
    }

    public String dedupKey() {
        return dedupKey;
    }

    public ChannelType channelOverride() {
        return channelOverride;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public static Builder builder(String messageType) {
        return new Builder(messageType);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    @Override
    public String toString() {
        return "MessageAction{type='%s', dedupKey='%s', orgId=%s, spaceId=%s}"
                .formatted(messageType, dedupKey, orgId, spaceId);
    }

    public static final class Builder {
        private final String messageType;
        private UUID orgId;
        private UUID spaceId;
        private String dedupKey;
        private ChannelType channelOverride;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(String messageType) {
            this.messageType = messageType;
        }

        public Builder orgId(UUID orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder spaceId(UUID spaceId) {
            this.spaceId = spaceId;
            return this;
        }

        public Builder dedupKey(String dedupKey) {
            this.dedupKey = dedupKey;
            return this;
        }

        public Builder channelOverride(ChannelType channelOverride) {
            this.channelOverride = channelOverride;
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("attribute key is required");
            }
            if (value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, ?> values) {
            if (values != null) {
                values.forEach(this::attribute);
            }
            return this;
        }

        public MessageAction build() {
            return new MessageAction(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageAction that)) return false;
        return messageType.equals(that.messageType)
                && Objects.equals(orgId, that.orgId)
                && Objects.equals(spaceId, that.spaceId)
                && dedupKey.equals(that.dedupKey)
                && Objects.equals(channelOverride, that.channelOverride)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageType, orgId, spaceId, dedupKey, channelOverride, attributes);
    }
}
