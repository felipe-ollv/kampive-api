package com.kampive.api.module.campsite.application.usecase;

import java.util.UUID;
import com.kampive.api.module.campsite.application.port.in.Campsites;
import com.kampive.api.module.campsite.application.port.out.CampsiteRepository;
import com.kampive.api.module.campsite.domain.CampsiteFailure;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import static com.kampive.api.module.campsite.domain.CampsiteFailure.Reason.*;

public final class CampsiteService implements Campsites {
    private final CampsiteRepository repository;
    private final UserAccounts users;
    public CampsiteService(CampsiteRepository repository, UserAccounts users) { this.repository=repository; this.users=users; }
    public Catalog catalog() { return repository.catalog(); }
    public Page<City> cities(Query q) { return repository.cities(q); }
    public Page<Summary> search(Query q) { return repository.search(q,null); }
    public Detail get(UUID uuid) { return detail(uuid,null); }
    public Page<Summary> mine(UUID actor, Query q) { active(actor); return repository.search(q,actor); }
    public Detail owned(UUID actor, UUID uuid) { active(actor); return detail(uuid,actor); }
    public Submission register(UUID actor, Content content) {
        active(actor);
        if (!content.readyForReview()) throw new CampsiteFailure(INVALID_INPUT);
        return new Submission(repository.register(actor,content),"pending_review");
    }
    public Submission suggest(UUID actor, SuggestionInput input) {
        active(actor); return repository.suggest(actor,input);
    }
    public Submission claim(UUID actor, UUID uuid, ClaimInput input) {
        active(actor);
        return repository.claim(actor,uuid,input).orElseThrow(() -> new CampsiteFailure(INVALID_STATE));
    }
    public Detail update(UUID actor, UUID uuid, Content content) {
        editable(actor,uuid); repository.update(uuid,content); repository.status(uuid,"draft"); return detail(uuid,actor);
    }
    public Detail submit(UUID actor, UUID uuid) {
        editable(actor,uuid);
        if (!detail(uuid,actor).content().readyForReview()) throw new CampsiteFailure(INVALID_INPUT);
        repository.status(uuid,"pending_review"); return detail(uuid,actor);
    }
    public Detail archive(UUID actor, UUID uuid) {
        lock(actor,uuid); repository.status(uuid,"archived"); return detail(uuid,actor);
    }
    private void editable(UUID actor, UUID uuid) {
        String state = lock(actor,uuid);
        if (!state.equals("draft") && !state.equals("rejected")) throw new CampsiteFailure(INVALID_STATE);
    }
    private String lock(UUID actor, UUID uuid) {
        active(actor); return repository.lockOwned(uuid,actor).orElseThrow(() -> new CampsiteFailure(NOT_FOUND));
    }
    private Detail detail(UUID uuid, UUID owner) {
        return repository.find(uuid,owner).orElseThrow(() -> new CampsiteFailure(NOT_FOUND));
    }
    private void active(UUID actor) {
        if (actor == null || users.findActiveByUuid(actor).isEmpty()) throw new CampsiteFailure(UNAUTHENTICATED);
    }
}
