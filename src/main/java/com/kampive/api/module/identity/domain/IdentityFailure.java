package com.kampive.api.module.identity.domain;

public final class IdentityFailure extends RuntimeException {
    public enum Reason { INVALID_INPUT, INVALID_CREDENTIALS, REGISTRATION_CONFLICT, DOCUMENTS_REQUIRED }
    private final Reason reason;
    public IdentityFailure(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
