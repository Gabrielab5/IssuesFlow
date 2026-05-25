package com.att.tdp.issueflow.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit-log recording via AOP.
 * idExpression is a SpEL expression evaluated with method parameters bound by name plus
 * #result bound to the return value. Use "#result?.id" for methods that return a DTO,
 * or "#paramName" for void methods where the entity ID comes from an argument.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    String entityType();
    String idExpression() default "#result?.id";
}
