package com.kampive.api.module.campsite.adapter.in.web;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.kampive.api.module.campsite.domain.CampsiteFailure;

@RestControllerAdvice
@Order(-1)
public class CampsiteExceptionHandler {
    @ExceptionHandler(CampsiteFailure.class)
    public ProblemDetail handle(CampsiteFailure failure) {
        int status=switch(failure.reason()) {
            case INVALID_INPUT -> 400;
            case NOT_FOUND -> 404;
            case INVALID_STATE -> 409;
            case UNAUTHENTICATED -> 401;
        };
        String detail=switch(failure.reason()) {
            case INVALID_INPUT -> "Invalid campsite data or catalog reference.";
            case NOT_FOUND -> "Campsite not found.";
            case INVALID_STATE -> "The campsite cannot be changed in its current state.";
            case UNAUTHENTICATED -> "An active account is required.";
        };
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.valueOf(status),detail);
    }
}
