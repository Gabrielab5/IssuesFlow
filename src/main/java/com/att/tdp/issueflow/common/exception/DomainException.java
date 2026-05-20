package com.att.tdp.issueflow.common.exception;

/**
 * Sealed root for all domain exceptions. Subclasses map to specific HTTP status codes
 * in {@link com.att.tdp.issueflow.common.error.GlobalExceptionHandler}.
 */
public sealed class DomainException extends RuntimeException
        permits NotFoundException, ConflictException, ForbiddenException,
                ValidationException, BusinessRuleException {

    protected DomainException(String message) {
        super(message);
    }
}
