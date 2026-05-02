package com.takibo.identitycore.integration.space.annotations;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireActiveSpace {
    /** Noms possibles de la path variable qui contient l’ID du Space. */
    String[] pathVarKeys() default { "spaceId", "sid" };
}
