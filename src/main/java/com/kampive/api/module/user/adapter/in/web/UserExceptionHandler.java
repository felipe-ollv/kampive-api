package com.kampive.api.module.user.adapter.in.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import com.kampive.api.module.user.domain.UserFailure;
@RestControllerAdvice
public class UserExceptionHandler {
    @ExceptionHandler(UserFailure.class)
    public ResponseEntity<ProblemDetail> handle(UserFailure e) {
        int status=e.reason()==UserFailure.Reason.INACTIVE?401:400;
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),"Invalid profile or inactive account."));
    }
}
