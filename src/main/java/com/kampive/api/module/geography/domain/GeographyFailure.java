package com.kampive.api.module.geography.domain;

public final class GeographyFailure extends RuntimeException {
    public enum Reason { INVALID_QUERY, COUNTRY_NOT_FOUND }
    private final Reason reason;
    public GeographyFailure(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
