package com.kampive.api.module.geography.adapter.in.web;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.kampive.api.module.geography.domain.GeographyFailure;

@Order(0)
@RestControllerAdvice
public class GeographyExceptionHandler {
    @ExceptionHandler(GeographyFailure.class)
    public ResponseEntity<ProblemDetail> handle(GeographyFailure failure) {
        boolean missing = failure.reason() == GeographyFailure.Reason.COUNTRY_NOT_FOUND;
        var status = missing ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String detail = missing ? "Country not found." : "Use page 0–10000, size 1–100 and query up to 120 characters.";
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
