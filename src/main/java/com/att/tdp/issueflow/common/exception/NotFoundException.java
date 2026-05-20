package com.att.tdp.issueflow.common.exception;

public final class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " not found with id: " + id);
    }
}
