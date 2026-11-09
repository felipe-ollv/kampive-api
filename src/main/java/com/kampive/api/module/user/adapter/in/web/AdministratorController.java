package com.kampive.api.module.user.adapter.in.web;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
public class AdministratorController {
    public record Access(UUID uuid, String role) {}
    // The security chain checks the current grant in the database on every request.
    @GetMapping("/api/v1/admin/me")
    public ResponseEntity<Access> me(Authentication authentication) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .body(new Access(UUID.fromString(authentication.getName()), "ADMIN"));
    }
}
