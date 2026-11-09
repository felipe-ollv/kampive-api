package com.kampive.api.module.favorite.adapter.in.web;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.kampive.api.module.favorite.application.port.in.Favorites;
@RestController
@RequestMapping("/api/v1/users/me/favorites")
public class FavoriteController {
    private final Favorites favorites;
    public FavoriteController(Favorites favorites) { this.favorites=favorites; }
    @GetMapping public List<UUID> list(Authentication auth) { return favorites.list(UUID.fromString(auth.getName())); }
    @PutMapping("/{uuid}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void save(Authentication auth,@PathVariable UUID uuid) { favorites.save(UUID.fromString(auth.getName()),uuid); }
    @DeleteMapping("/{uuid}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void remove(Authentication auth,@PathVariable UUID uuid) { favorites.remove(UUID.fromString(auth.getName()),uuid); }
}
