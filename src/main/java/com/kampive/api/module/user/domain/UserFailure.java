package com.kampive.api.module.user.domain;
public final class UserFailure extends RuntimeException {
    public enum Reason { INVALID_INPUT, INACTIVE }
    private final Reason reason;
    public UserFailure(Reason reason) { this.reason=reason; }
    public Reason reason() { return reason; }
}
