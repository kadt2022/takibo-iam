package com.takibo.audit.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
public @interface Mask {

    MaskMode mode() default MaskMode.FULL;

    int showLeft() default 0;

    int showRight() default 0;

    int fromLeft() default 0;

    int fromRight() default 0;

    double ratio() default 0.5;

    char symbol() default '*';

    String word() default "";

    int index() default -1;

    int[] indexes() default {};
}
