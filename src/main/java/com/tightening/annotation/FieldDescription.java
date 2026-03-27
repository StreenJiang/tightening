package com.tightening.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FieldDescription {
    /**
     * 消息键，对应 messages.properties 中的 key
     */
    String key();

    /**
     * 可选参数，用于替换消息中的占位符 {0}, {1}...
     */
    String[] args() default { };
}
