package com.att.tdp.issueflow.common.exception;

/** Thrown when a request is syntactically valid but violates a domain business rule. */
public final class BusinessRuleException extends DomainException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
