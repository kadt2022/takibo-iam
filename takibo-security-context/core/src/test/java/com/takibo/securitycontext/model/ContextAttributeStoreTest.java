package com.takibo.securitycontext.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAttributeStoreTest {

    private static final AttributeKey CUSTOM = new AttributeKey("custom", "flag");

    @Test
    void standardKeysLiveInTheirNamespaces() {
        assertEquals(new AttributeKey("entitlements", "permissions"), StandardAttributeKeys.PERMISSIONS);
        assertEquals(new AttributeKey("entitlements", "roles"), StandardAttributeKeys.ROLES);
        assertEquals(new AttributeKey("identity", "accountId"), StandardAttributeKeys.ACCOUNT_ID);
        assertEquals(new AttributeKey("identity", "userId"), StandardAttributeKeys.USER_ID);
        assertEquals(new AttributeKey("tenant", "scopeLevel"), StandardAttributeKeys.SCOPE_LEVEL);
    }

    @Test
    void storeIsAnImmutableSnapshot() {
        Map<AttributeKey, Object> source = new HashMap<>(Map.of(StandardAttributeKeys.ACCOUNT_ID, "acc-1"));
        ContextAttributeStore store = new ContextAttributeStore(source);
        source.put(CUSTOM, true);

        assertEquals(Map.of(StandardAttributeKeys.ACCOUNT_ID, "acc-1"), store.asMap());
        assertThrows(UnsupportedOperationException.class, () -> store.asMap().put(CUSTOM, false));
        assertTrue(new ContextAttributeStore(null).asMap().isEmpty());
    }

    @Test
    void get_returnsPresentOrEmpty() {
        ContextAttributeStore store = new ContextAttributeStore(Map.of(StandardAttributeKeys.ACCOUNT_ID, "acc-1"));

        assertEquals(Optional.of("acc-1"), store.get(StandardAttributeKeys.ACCOUNT_ID));
        assertEquals(Optional.empty(), store.get(StandardAttributeKeys.USER_ID));
    }

    @Test
    void typedGet_checksTheType() {
        ContextAttributeStore store = new ContextAttributeStore(Map.of(StandardAttributeKeys.ACCOUNT_ID, "acc-1"));

        assertEquals(Optional.of("acc-1"), store.get(StandardAttributeKeys.ACCOUNT_ID, String.class));
        assertEquals(Optional.empty(), store.get(StandardAttributeKeys.USER_ID, String.class));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.get(StandardAttributeKeys.ACCOUNT_ID, Integer.class));
        assertEquals("Attribute " + StandardAttributeKeys.ACCOUNT_ID + " is not a Integer", error.getMessage());
    }

    @Test
    void stringSet_returnsAnImmutableCopy_orEmptyWhenAbsent() {
        Set<String> permissions = new LinkedHashSet<>(List.of("users:read", "users:write"));
        ContextAttributeStore store = new ContextAttributeStore(Map.of(StandardAttributeKeys.PERMISSIONS, permissions));

        Set<String> result = store.getStringSet(StandardAttributeKeys.PERMISSIONS);
        assertEquals(Set.of("users:read", "users:write"), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add("admin"));
        assertEquals(Set.of(), store.getStringSet(StandardAttributeKeys.ROLES));
    }

    @Test
    void stringSet_rejectsNonSetsAndNonStringElements() {
        ContextAttributeStore store = new ContextAttributeStore(Map.of(
                StandardAttributeKeys.ROLES, List.of("ADMIN"),
                StandardAttributeKeys.PERMISSIONS, Set.of(42)));

        assertEquals("Attribute " + StandardAttributeKeys.ROLES + " is not a Set",
                assertThrows(IllegalArgumentException.class, () -> store.getStringSet(StandardAttributeKeys.ROLES)).getMessage());
        assertEquals("Attribute " + StandardAttributeKeys.PERMISSIONS + " is not a Set<String>",
                assertThrows(IllegalArgumentException.class, () -> store.getStringSet(StandardAttributeKeys.PERMISSIONS)).getMessage());
    }
}
