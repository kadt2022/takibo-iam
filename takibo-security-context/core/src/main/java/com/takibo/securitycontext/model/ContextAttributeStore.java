package com.takibo.securitycontext.model;

import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ContextAttributeStore {

    private final Map<AttributeKey, Object> values;

    public ContextAttributeStore(Map<AttributeKey, Object> values) {
        this.values = values == null ? Map.of() : Map.copyOf(values);
    }

    public Optional<Object> get(AttributeKey key) {
        return Optional.ofNullable(values.get(key));
    }

    public <T> Optional<T> get(AttributeKey key, Class<T> type) {
        Object v = values.get(key);
        if (v == null) return Optional.empty();
        if (!type.isInstance(v)) {
            throw new IllegalArgumentException("Attribute " + key + " is not a " + type.getSimpleName());
        }
        return Optional.of(type.cast(v));
    }

    public Set<String> getStringSet(AttributeKey key) {
        Object value = values.get(key);
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Set<?> set)) {
            throw new IllegalArgumentException("Attribute " + key + " is not a Set");
        }

        Set<String> strings = new LinkedHashSet<>(set.size());
        for (Object element : set) {
            if (!(element instanceof String stringValue)) {
                throw new IllegalArgumentException("Attribute " + key + " is not a Set<String>");
            }
            strings.add(stringValue);
        }
        return Set.copyOf(strings);
    }

    public Map<AttributeKey, Object> asMap() {
        return values;
    }
}
