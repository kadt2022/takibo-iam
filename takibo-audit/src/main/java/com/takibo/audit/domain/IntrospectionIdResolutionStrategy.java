package com.takibo.audit.domain;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20) // 3e (dernier)
@Slf4j
public class IntrospectionIdResolutionStrategy implements IdResolutionStrategy {

    private static final List<String> COMMON_ID_FIELDS = List.of("id", "uuid");

    @Override
    public Optional<String> resolve(JoinPoint joinPoint, String expression, Object result) {
        try {
            // 1) Cherche dans les arguments
            for (Object arg : joinPoint.getArgs()) {
                var id = findIdInObject(arg);
                if (id.isPresent()) return id;
            }

            // 2) Cherche dans le résultat
            var id = findIdInObject(result);
            if (id.isPresent()) return id;

            // 3) Fallback : String direct uniquement
            for (Object arg : joinPoint.getArgs()) {
                if (arg instanceof String s) return Optional.of(s);
            }

            return Optional.empty();

        } catch (Exception e) {
            log.warn("Échec de l'extraction d'ID par réflexion", e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("java:S3011") // Justifié : introspection d'entités sans getter public pour l'ID
    private Optional<String> findIdInObject(Object obj) {
        if (obj == null) return Optional.empty();

        if (obj instanceof ResponseEntity<?> resp) {
            return findIdInObject(resp.getBody());
        }
        if (obj instanceof Collection<?> coll && !coll.isEmpty()) {
            return findIdInObject(coll.iterator().next());
        }

        for (String fieldName : COMMON_ID_FIELDS) {
            try {
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null) return Optional.of(value.toString());
            } catch (NoSuchFieldException | IllegalAccessException ignore) {
                log.trace("Champ {} introuvable/inaccessible sur {}", fieldName, obj.getClass().getSimpleName());
            }
        }
        return Optional.empty();
    }
}
