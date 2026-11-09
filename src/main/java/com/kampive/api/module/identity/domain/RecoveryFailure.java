package com.kampive.api.module.identity.domain;
public class RecoveryFailure extends RuntimeException {
    public enum Reason { UNAVAILABLE, INVALID_TOKEN, INVALID_PASSWORD }
    private final Reason reason;
    public RecoveryFailure(Reason reason) { super("Password recovery failed"); this.reason=reason; }
    public Reason reason() { return reason; }
}
