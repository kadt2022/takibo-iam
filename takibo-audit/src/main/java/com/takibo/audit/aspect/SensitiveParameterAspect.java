package com.takibo.audit.aspect;

import com.takibo.audit.annotations.Sensitive;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Aspect qui intercepte tous les paramètres annotés {@link Sensitive}.
 * <p>
 *  • Dans les logs manuels (ex. log.info(param)), le toString() doit toujours renvoyer "*************".<br>
 *  • Le type d’origine est conservé : <br>
 *    – proxy JDK si l’objet implémente au moins une interface ;<br>
 *    – sous‑classe ByteBuddy sinon (si la classe n’est pas final).<br>
 *  • Si la classe est final ou que la génération échoue, un wrapper anonyme est utilisé (même masque).
 */
@Aspect
@Component
@Slf4j
public class SensitiveParameterAspect {

    private static final String MASK = "*************";

    @Around("execution(* *(.., @com.takibo.audit.annotations.Sensitive (*), ..))")
    public Object interceptSensitiveParams(ProceedingJoinPoint pjp) throws Throwable {

        Object[] args = pjp.getArgs();
        Parameter[] params =
                ((MethodSignature) pjp.getSignature()).getMethod().getParameters();

        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(Sensitive.class) && args[i] != null) {
                args[i] = maskToString(args[i]);
            }
        }
        return pjp.proceed(args);
    }

    /* ===================================================================== */
    /* ======================  CORE MASKING LOGIC  ========================= */
    /* ===================================================================== */

    /** Retourne un objet du même type (ou compatible) dont toString() est masqué. */
    private Object maskToString(Object original) {
        Class<?> type = original.getClass();

        /* Proxy JDK si l’objet implémente ≥1 interface */
        if (type.getInterfaces().length > 0) {
            return Proxy.newProxyInstance(
                    type.getClassLoader(),
                    type.getInterfaces(),
                    (proxy, method, args) ->
                            "toString".equals(method.getName())
                                    ? MASK
                                    : method.invoke(original, args)
            );
        }

        /*   Sous‑classe dynamique ByteBuddy (si la classe n’est pas final) */
        if (!Modifier.isFinal(type.getModifiers())) {
            try {
                Class<?> subClass = new ByteBuddy()
                        .subclass(type)                     //  ←  correction : pas de param Visibility dans subclass
                        .modifiers(Visibility.PUBLIC)       //  ←  on rend la sous‑classe publique
                        .method(named("toString"))
                        .intercept(FixedValue.value(MASK))
                        .make()
                        .load(type.getClassLoader(),
                                ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded();

                Object copy = subClass.getDeclaredConstructor().newInstance();
                copyFields(original, copy);
                return copy;

            } catch (Exception e) {
                log.warn("ByteBuddy subclassing failed, fallback wrapper will be used", e);
            }
        }

        /*   wrapper anonyme (type différent) */
        return new Object() {
            @Override public String toString() { return MASK; }
        };
    }

    /** Copie les champs (même privés) de src vers dest pour conserver l’état métier. */
    private void copyFields(Object src, Object dest) {
        Class<?> c = src.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    f.set(dest, f.get(src));
                } catch (IllegalAccessException ignored) {}
            }
            c = c.getSuperclass();
        }
    }
}
