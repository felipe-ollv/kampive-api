package com.kampive.api.module.favorite.application.port.in;
import java.util.List;
import java.util.UUID;
public interface Favorites {
    List<UUID> list(UUID actor);
    void save(UUID actor, UUID campsite);
    void remove(UUID actor, UUID campsite);
}
