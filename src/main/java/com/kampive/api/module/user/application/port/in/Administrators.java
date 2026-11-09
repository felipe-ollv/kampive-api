package com.kampive.api.module.user.application.port.in;

import java.util.UUID;

/** Administrative grants are managed out of band, never through profile input. */
public interface Administrators {
    boolean active(UUID actor);
}
