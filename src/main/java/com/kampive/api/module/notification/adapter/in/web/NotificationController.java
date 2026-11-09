package com.kampive.api.module.notification.adapter.in.web;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.kampive.api.module.notification.application.port.in.Notifications;
@RestController @RequestMapping("/api/v1/users/me/notifications")
public class NotificationController {
    private final Notifications notifications;
    public NotificationController(Notifications notifications) { this.notifications=notifications; }
    public record ReadInput(@NotNull Boolean read) {}
    @GetMapping public Notifications.Inbox list(Authentication auth,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="all") String filter) {
        return notifications.list(UUID.fromString(auth.getName()),page,size,filter);
    }
    @PutMapping("/{uuid}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void read(Authentication auth,@PathVariable UUID uuid,@Valid @RequestBody ReadInput input) {
        notifications.read(UUID.fromString(auth.getName()),uuid,input.read());
    }
    @PostMapping("/read-all") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void readAll(Authentication auth) { notifications.readAll(UUID.fromString(auth.getName())); }
}
