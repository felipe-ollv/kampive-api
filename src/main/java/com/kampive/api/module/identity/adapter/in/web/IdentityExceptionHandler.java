package com.kampive.api.module.identity.adapter.in.web;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.kampive.api.module.identity.domain.IdentityFailure;

@Order(0)
@RestControllerAdvice
public class IdentityExceptionHandler {
    @ExceptionHandler(IdentityFailure.class)
    public ResponseEntity<ProblemDetail> handle(IdentityFailure failure) {
        HttpStatus status=switch(failure.reason()) {
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case REGISTRATION_CONFLICT -> HttpStatus.CONFLICT;
            case DOCUMENTS_REQUIRED, INVALID_INPUT -> HttpStatus.BAD_REQUEST;
        };
        String detail=switch(failure.reason()) {
            case INVALID_CREDENTIALS -> "Invalid email or password.";
            case REGISTRATION_CONFLICT -> "The account could not be registered with the supplied information.";
            case DOCUMENTS_REQUIRED -> "Accept the currently published terms and privacy policy.";
            case INVALID_INPUT -> "The supplied account information is invalid.";
        };
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status,detail));
    }
}
