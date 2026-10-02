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
    public UserView create(String name, String email, UUID country, UUID style) {
        return users.create(name, email, country, style);
    }
}
