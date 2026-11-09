package com.kampive.api.module.campsite.application.port.out;

import java.util.UUID;
import java.util.Optional;
import com.kampive.api.module.campsite.application.port.in.Campsites.*;

public interface CampsiteRepository {
    Catalog catalog();
    Page<City> cities(Query query);
    Page<Summary> search(Query query, UUID owner);
    Optional<Detail> find(UUID uuid, UUID owner);
    Optional<String> lockOwned(UUID uuid, UUID owner);
    UUID register(UUID actor, Content content);
    Submission suggest(UUID actor, SuggestionInput input);
    Optional<Submission> claim(UUID actor, UUID uuid, ClaimInput input);
    void update(UUID uuid, Content content);
    void status(UUID uuid, String status);
}
