package com.kampive.api.module.user.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** The identity module accesses users exclusively through this input contract. */
public interface UserAccounts {
    record UserView(UUID uuid, String name, String email, UUID countryUuid,
                    UUID adventurerStyleUuid, String photoUrl, String bio,
                    boolean notificationsEnabled, boolean emailVerified) {}
    Optional<UserView> findActiveByEmail(String email);
    Optional<UserView> findActiveByUuid(UUID uuid);
    UserView create(String name, String email, UUID countryUuid, UUID adventurerStyleUuid);
}
