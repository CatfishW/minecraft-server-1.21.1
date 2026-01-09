package com.novus.auth;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AuthManager {
    private final Set<UUID> authenticatedPlayers = new HashSet<>();

    public void authenticate(UUID uuid) {
        authenticatedPlayers.add(uuid);
    }

    public void deauthenticate(UUID uuid) {
        authenticatedPlayers.remove(uuid);
    }

    public boolean isAuthenticated(UUID uuid) {
        // If auth is not required (single player mode), always return true
        if (!NovusAuth.isAuthRequired()) {
            return true;
        }
        return authenticatedPlayers.contains(uuid);
    }
}
