package com.kampive.api.module.identity.adapter.in.web;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.kampive.api.module.identity.application.port.in.EmailVerification;
import com.kampive.api.module.identity.domain.RecoveryFailure;
@RestController
@RequestMapping("/api/v1/auth/email-verification")
public class EmailVerificationController {
    public record Confirm(@NotBlank @Size(max=128) String token) { @Override public String toString(){return "Confirm[REDACTED]";} }
    private final EmailVerification verification;
    private final AuthRateLimiter limiter;
    public EmailVerificationController(EmailVerification verification,AuthRateLimiter limiter) {this.verification=verification;this.limiter=limiter;}
    @GetMapping public ResponseEntity<?> status(Principal principal) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(verification.status(UUID.fromString(principal.getName())));
    }
    @PostMapping("/resend") public ResponseEntity<?> resend(Principal principal,HttpServletRequest request) {
        if(!limiter.allow("verify-resend:"+request.getRemoteAddr())) return limited();
        verification.resend(UUID.fromString(principal.getName()));
        return ResponseEntity.accepted().header("Cache-Control","no-store").body(Map.of("status","accepted"));
    }
    @PostMapping("/confirm") public ResponseEntity<?> confirm(@Valid @RequestBody Confirm body,HttpServletRequest request) {
        if(!limiter.allow("verify-confirm:"+request.getRemoteAddr())) return limited();
        verification.confirm(body.token());
        return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    private ResponseEntity<?> limited(){return ResponseEntity.status(429).header("Retry-After","60").build();}
    @ExceptionHandler(RecoveryFailure.class) ResponseEntity<?> failure(RecoveryFailure e) {
        var status=e.reason()==RecoveryFailure.Reason.UNAVAILABLE?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.BAD_REQUEST;
        var problem=ProblemDetail.forStatus(status);problem.setProperty("code",e.reason().name());
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(problem);
    }
}
