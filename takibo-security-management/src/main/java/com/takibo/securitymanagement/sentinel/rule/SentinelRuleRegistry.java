package com.takibo.securitymanagement.sentinel.rule;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SentinelRuleRegistry {

    private final Map<Class<? extends Throwable>, SentinelRule<? extends Throwable>> rules = new ConcurrentHashMap<>();
    private final Map<Class<?>, SentinelRule<? extends Throwable>> cache = new ConcurrentHashMap<>();
    private final SentinelRule<Throwable> fallback;

    public SentinelRuleRegistry(SentinelRule<Throwable> fallback) {
        this.fallback = fallback;
    }

    public <T extends Throwable> void register(Class<T> type, SentinelRule<T> sentinelRule) {
        rules.put(type, sentinelRule);
    }

    /**
     * Enregistrement "soft" par FQCN pour éviter la dépendance compile-time.
     * Retourne false si la classe n'existe pas ou n'est pas une Throwable.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean tryRegister(String fqcn, SentinelRule<? extends Throwable> sentinelRule) {
        try {
            Class<?> c = Class.forName(fqcn);
            if (Throwable.class.isAssignableFrom(c)) {
                register((Class<? extends Throwable>) c, (SentinelRule) sentinelRule);
                return true;
            }
        } catch (ClassNotFoundException ignore) {
            // pas de spring-security par ex. -> on ignore
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public SentinelRule<Throwable> resolve(Throwable ex) {
        Class<?> type = ex.getClass();

        SentinelRule<? extends Throwable> cached = cache.get(type);
        if (cached != null) return (SentinelRule<Throwable>) cached;

        // 1) match exact
        SentinelRule<? extends Throwable> r = rules.get(type);
        if (r != null) {
            cache.put(type, r);
            return (SentinelRule<Throwable>) r;
        }

        // 2) remonter la hiérarchie
        Class<?> cur = type.getSuperclass();
        while (cur != null && Throwable.class.isAssignableFrom(cur)) {
            r = rules.get(cur);
            if (r != null) {
                cache.put(type, r);
                return (SentinelRule<Throwable>) r;
            }
            cur = cur.getSuperclass();
        }

        for (Class<?> itf : type.getInterfaces()) {
            if (Throwable.class.isAssignableFrom(itf)) {
                r = rules.get(itf.asSubclass(Throwable.class));
                if (r != null) {
                    cache.put(type, r);
                    return (SentinelRule<Throwable>) r;
                }
            }
        }

        return fallback;
    }
}
