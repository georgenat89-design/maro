package dev.maro.runtime.event;

import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface EventHandler { int priority() default 0; }
