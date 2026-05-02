package com.takibo.audit.infrastructure.service;

import com.takibo.audit.annotations.AuditIgnore;
import com.takibo.audit.annotations.Mask;
import com.takibo.audit.annotations.MaskMode;
import com.takibo.audit.annotations.Sensitive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.time.temporal.Temporal;
import java.util.*;

/**
 * Règles de priorité :
 *   1) @Sensitive      -> valeur totalement silencieuse (********) et/ou exclusion
 *   2) @AuditIgnore    -> omission (le champ n'apparaît pas)
 *   3) @Mask           -> application de la stratégie déclarée
 *   4) Heuristique noms sensibles (password, token, ...)
 *
 * Remarques :
 *  - Pour les records, on lit via les accessors (pas de setAccessible).
 *  - Pour les POJOs Takibo, on peut ouvrir en réflexion (JDK 21 OK).
 *  - Pour les classes JDK/3rd-party, on ne creuse pas -> toString "safe".
 */
@Slf4j
@Service
public class MaskingService {

    private static final String DEFAULT_MASK = "********";

    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "secret","clientSecret",
            "token","accessToken","refreshToken","authorization",
            "otp","mfa","privateKey","apiKey"
    );

    /** Construit la map <paramName, valeur masquée> pour l’événement/log. */
    public Map<String, Object> mask(Method method, Object[] args) {
        Map<String, Object> out = new LinkedHashMap<>();
        Parameter[] params = method.getParameters();

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];

            // Exclusion totale au niveau paramètre
            if (p.isAnnotationPresent(AuditIgnore.class)) {
                continue;
            }
            if (p.isAnnotationPresent(Sensitive.class)) {
                out.put(paramName(p, i), DEFAULT_MASK);
                continue;
            }

            Object value = args[i];

            // Masque au niveau paramètre si String + @Mask
            Mask pm = p.getAnnotation(Mask.class);
            if (pm != null && value instanceof CharSequence cs) {
                out.put(paramName(p, i), applyMask(cs.toString(), pm));
                continue;
            }

            // Sinon on descend récursivement
            out.put(paramName(p, i), maskValue(value, new IdentityHashMap<>()));
        }
        return out;
    }

    /**
     * Point d'entrée pour générer une version masquée d'un objet
     * pour les logs (toString, traces techniques, etc.).
     */
    public Object maskForLogging(Object value) {
        return maskValue(value, new IdentityHashMap<>());
    }


    /* ============================ cœur ============================ */

    private Object maskValue(Object v, Map<Object, Boolean> seen) {
        if (v == null) return null;

        if (seen.put(v, Boolean.TRUE) != null) return "[CIRCULAR-REF]";

        Class<?> c = v.getClass();

        // feuilles
        if (isLeaf(c)) return v;

        // tableaux
        if (c.isArray()) {
            int len = Array.getLength(v);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) list.add(maskValue(Array.get(v, i), seen));
            return list;
        }

        // collections
        if (v instanceof Collection<?> col) {
            List<Object> list = new ArrayList<>(col.size());
            for (Object e : col) list.add(maskValue(e, seen));
            return list;
        }

        // maps (heuristique sur les clés)
        if (v instanceof Map<?, ?> map) {
            Map<Object,Object> m = new LinkedHashMap<>(map.size());
            for (var e : map.entrySet()) {
                Object key = e.getKey();
                Object val = e.getValue();
                if (key instanceof String k && isSensitiveName(k)) {
                    m.put(k, DEFAULT_MASK);
                } else {
                    m.put(key, maskValue(val, seen));
                }
            }
            return m;
        }

        // records : utilise les accessors + annotations sur components
        if (c.isRecord()) {
            Map<String,Object> rec = new LinkedHashMap<>();
            for (RecordComponent rc : c.getRecordComponents()) {
                String name = rc.getName();

                // priorité @AuditIgnore
                if (rc.isAnnotationPresent(AuditIgnore.class)) {
                    continue;
                }
                // priorité @Sensitive
                if (rc.isAnnotationPresent(Sensitive.class)) {
                    rec.put(name, DEFAULT_MASK);
                    continue;
                }
                Object val;
                try {
                    val = rc.getAccessor().invoke(v);
                } catch (Throwable t) {
                    rec.put(name, "<unavailable>");
                    continue;
                }
                // @Mask
                Mask m = rc.getAnnotation(Mask.class);
                if (m != null) {
                    rec.put(name, applyMask(String.valueOf(val), m));
                    continue;
                }

                // heuristique nom sensible
                if (isSensitiveName(name)) {
                    rec.put(name, DEFAULT_MASK);
                    continue;
                }

                // recurse
                rec.put(name, maskValue(val, seen));
            }
            return rec;
        }

        // POJO Takibo : introspection contrôlée
        if (isTakiboPojo(c)) {
            Map<String,Object> out = new LinkedHashMap<>();
            for (Field f : allFields(c)) {
                if (!isMaskableField(f)) continue;
                String name = f.getName();

                // @AuditIgnore > @Sensitive > @Mask > heuristique
                if (f.isAnnotationPresent(AuditIgnore.class)) {
                    continue;
                }
                if (f.isAnnotationPresent(Sensitive.class) || isSensitiveName(name)) {
                    out.put(name, DEFAULT_MASK);
                    continue;
                }

                Object val = getFieldSafely(f, v);

                Mask m = f.getAnnotation(Mask.class);
                if (m != null) {
                    out.put(name, applyMask(String.valueOf(val), m));
                    continue;
                }

                out.put(name, maskValue(val, seen));
            }
            return out;
        }

        // fallback conservatif
        return safeToString(v);
    }

    /* ============================ apply @Mask ============================ */

    private String applyMask(String s, Mask m) {
        if (s == null) return null;
        String str = s;
        int len = str.length();
        if (len == 0) return "";

        char sym = m.symbol();

        switch (m.mode()) {
            case FULL -> {
                return repeat(sym, len);
            }
            case LEFT -> {
                int keep = clamp(m.showRight(), 0, len);
                return repeat(sym, len - keep) + str.substring(len - keep);
            }
            case RIGHT -> {
                int keep = clamp(m.showLeft(), 0, len);
                return str.substring(0, keep) + repeat(sym, len - keep);
            }
            case CENTER -> {
                int left = clamp(m.showLeft(), 0, len);
                int right = clamp(m.showRight(), 0, len - left);
                int mid = Math.max(0, len - left - right);
                return str.substring(0, left) + repeat(sym, mid) + str.substring(len - right);
            }
            case FROM_LEFT -> {
                int n = clamp(m.fromLeft(), 0, len);
                return str.substring(0, n) + repeat(sym, len - n);
            }
            case FROM_RIGHT -> {
                int n = clamp(m.fromRight(), 0, len);
                return repeat(sym, len - n) + str.substring(len - n);
            }
            case RATIO -> {
                double r = Math.max(0.0, Math.min(1.0, m.ratio()));
                int maskCount = (int) Math.round(len * r);
                maskCount = clamp(maskCount, 0, len);
                int keep = len - maskCount;
                int left = keep / 2;
                int right = keep - left;
                return str.substring(0, left) + repeat(sym, maskCount) + str.substring(len - right);
            }
            case SYMBOLIC -> {
                return repeat(m.symbol(), len);
            }
            case WORD -> {
                String w = m.word();
                if (w == null || w.isBlank()) return repeat(sym, len);
                return replaceWordCaseInsensitive(str, w, repeat(sym, w.length()));
            }
            case INDEX -> {
                boolean[] maskPos = new boolean[len];
                Arrays.fill(maskPos, false);

                int idx = m.index();
                if (idx >= 0 && idx < len) maskPos[idx] = true;

                for (int i : m.indexes()) {
                    if (i >= 0 && i < len) maskPos[i] = true;
                }
                StringBuilder sb = new StringBuilder(len);
                for (int i = 0; i < len; i++) {
                    sb.append(maskPos[i] ? sym : str.charAt(i));
                }
                return sb.toString();
            }
            default -> {
                return repeat(sym, len);
            }
        }
    }

    private static String replaceWordCaseInsensitive(String s, String word, String repl) {
        String src = s;
        String lw = word.toLowerCase(Locale.ROOT);
        String ls = src.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            int pos = ls.indexOf(lw, i);
            if (pos < 0) {
                out.append(src, i, src.length());
                break;
            }
            out.append(src, i, pos).append(repl);
            i = pos + lw.length();
        }
        return out.toString();
    }

    /* ============================ utilitaires ============================ */

    private static String paramName(Parameter p, int i) {
        // Si pas de -parameters au compile, p.getName() = "arg0/arg1"
        return p.getName() != null ? p.getName() : ("arg" + i);
    }

    private boolean isSensitiveName(String n) {
        if (n == null) return false;
        String s = n.toLowerCase(Locale.ROOT);
        return SENSITIVE_NAMES.contains(s)
                || s.contains("pass") || s.contains("secret") || s.contains("token") || s.contains("key");
    }

    private boolean isLeaf(Class<?> c) {
        return c.isPrimitive()
                || CharSequence.class.isAssignableFrom(c)
                || Number.class.isAssignableFrom(c)
                || Boolean.class == c || Character.class == c
                || BigDecimal.class == c || BigInteger.class == c
                || UUID.class == c
                || Date.class.isAssignableFrom(c)
                || Temporal.class.isAssignableFrom(c)
                || InetAddress.class.isAssignableFrom(c)
                || URI.class.isAssignableFrom(c) || URL.class.isAssignableFrom(c)
                || c.isEnum();
    }

    private boolean isJdkOr3rdParty(Class<?> c) {
        Package p = c.getPackage();
        String pn = (p == null) ? "" : p.getName();
        return pn.startsWith("java.")
                || pn.startsWith("javax.")
                || pn.startsWith("jakarta.")
                || pn.startsWith("org.springframework.")
                || pn.startsWith("com.fasterxml.")
                || pn.startsWith("org.hibernate.")
                || pn.startsWith("com.mysql.");
    }

    private boolean isTakiboPojo(Class<?> c) {
        Package p = c.getPackage();
        String pn = (p == null) ? "" : p.getName();
        return pn.startsWith("com.takibo.") || pn.startsWith("com.takibu.");
    }

    private List<Field> allFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        Class<?> t = c;
        while (t != null && t != Object.class) {
            Collections.addAll(out, t.getDeclaredFields());
            t = t.getSuperclass();
        }
        return out;
    }

    private boolean isMaskableField(Field f) {
        int m = f.getModifiers();
        if (Modifier.isStatic(m) || Modifier.isTransient(m) || f.isSynthetic()) return false;
        return !"serialVersionUID".equals(f.getName());
    }

    private Object getFieldSafely(Field f, Object target) {
        try {
            if (!f.canAccess(target)) {
                if (isTakiboPojo(f.getDeclaringClass())) f.setAccessible(true);
                else return safeToString("<unavailable>");
            }
            return f.get(target);
        } catch (Throwable e) {
            return safeToString("<unavailable>");
        }
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static String repeat(char c, int n) {
        if (n <= 0) return "";
        char[] arr = new char[n];
        Arrays.fill(arr, c);
        return new String(arr);
    }

    private String safeToString(Object v) {
        try { return String.valueOf(v); }
        catch (Throwable t) { return "<unprintable>"; }
    }
}
