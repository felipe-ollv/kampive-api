package com.kampive.api.module.user.application.port.in;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** The identity module accesses users exclusively through this input contract. */
public interface UserAccounts {
    record UserView(UUID uuid, String name, String email, UUID countryUuid,
                    UUID adventurerStyleUuid, String photoUrl, String bio,
                    boolean notificationsEnabled, boolean emailVerified,
                    UUID cityUuid, String cityName, String divisionName, String countryName, List<String> travelStyles, UUID divisionUuid) {
        public UserView(UUID uuid, String name, String email, UUID countryUuid, UUID adventurerStyleUuid,
                String photoUrl, String bio, boolean notificationsEnabled, boolean emailVerified) {
            this(uuid,name,email,countryUuid,adventurerStyleUuid,photoUrl,bio,notificationsEnabled,emailVerified,
                null,null,null,null,List.of(),null);
        }
    }
    record ProfileInput(String name, UUID countryUuid, UUID cityUuid, String bio, List<String> travelStyles,
                        String photoUrl) {}
    UserView update(UUID actor, ProfileInput input);
    void delete(UUID actor);
    Optional<UserView> findActiveByEmail(String email);
    Optional<UserView> findActiveByUuid(UUID uuid);
    UserView create(String name, String email, UUID countryUuid, UUID adventurerStyleUuid);
}
