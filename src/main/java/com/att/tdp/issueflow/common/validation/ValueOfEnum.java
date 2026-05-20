package com.att.tdp.issueflow.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a String field matches a constant name of the given enum.
 * Null values are considered valid — combine with {@code @NotNull} when required.
 *
 * <pre>{@code
 * @ValueOfEnum(enumClass = TicketStatus.class)
 * private String status;
 * }</pre>
 */
@Documented
@Constraint(validatedBy = ValueOfEnumValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueOfEnum {

    Class<? extends Enum<?>> enumClass();

    String message() default "must be one of the accepted values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
