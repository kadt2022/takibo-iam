package com.takibo.messaging.domain;

import java.util.Objects;

public final class Recipient {

    private final String type;
    private final String value;
    private final String key;

    public Recipient(String type, String value, String key) {
        this.type = require(type, "type");
        this.value = require(value, "value");
        this.key = require(key, "key");
    }

    public String type() {
        return type;
    }

    public String value() {
        return value;
    }

    public String key() {
        return key;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Recipient that)) return false;
        return type.equals(that.type) && key.equals(that.key) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, key);
    }

    @Override
    public String toString() {
        return "Recipient{type='%s', key='%s', value='%s'}".formatted(type, key, value);
    }
}
