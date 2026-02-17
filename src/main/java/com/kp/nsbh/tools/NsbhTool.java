package com.kp.nsbh.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NsbhTool {
    String name();

    String description();

    String schema() default "{}";

    String[] requiredPermissions() default {};
}
