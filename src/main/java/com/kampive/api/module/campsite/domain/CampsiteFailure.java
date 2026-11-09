package com.kampive.api.module.campsite.domain;

public final class CampsiteFailure extends RuntimeException {
    public enum Reason { INVALID_INPUT, NOT_FOUND, INVALID_STATE, UNAUTHENTICATED }
    private final Reason reason;
    public CampsiteFailure(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
