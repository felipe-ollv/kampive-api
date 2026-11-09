package com.kampive.api.module.user.application.usecase;

import java.util.Optional;
import java.util.UUID;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import com.kampive.api.module.user.application.port.out.UserRepository;

public final class UserService implements UserAccounts {
    private final UserRepository users;
    public UserService(UserRepository users) { this.users = users; }
    public Optional<UserView> findActiveByEmail(String email) { return users.findActiveByEmail(email); }
    public Optional<UserView> findActiveByUuid(UUID uuid) { return users.findActiveByUuid(uuid); }
    public UserView update(UUID actor, ProfileInput input) {
        if (input == null || input.name() == null || input.name().strip().length()<3 || input.name().strip().length()>150
            || input.bio()==null || input.bio().length()>500 || input.travelStyles()==null
            || input.travelStyles().size()>4 || input.travelStyles().stream().anyMatch(s -> s==null || !java.util.Set.of("tent","motorhome","trailer","chalet").contains(s))
            || new java.util.HashSet<>(input.travelStyles()).size()!=input.travelStyles().size()
            || (input.cityUuid()!=null && input.countryUuid()==null))
            throw new com.kampive.api.module.user.domain.UserFailure(com.kampive.api.module.user.domain.UserFailure.Reason.INVALID_INPUT);
        return users.update(actor,new ProfileInput(input.name().strip(),input.countryUuid(),input.cityUuid(),
            input.bio().strip(),java.util.List.copyOf(input.travelStyles()),input.photoUrl()));
    }
    public void delete(UUID actor) { users.delete(actor); }
    public UserView create(String name, String email, UUID country, UUID style) {
        return users.create(name, email, country, style);
    }
}
