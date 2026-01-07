package com.novus.auth;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuthService {
    CompletableFuture<AuthResult> register(UUID playerUuid, String username, String password);
    CompletableFuture<AuthResult> login(UUID playerUuid, String password);
    CompletableFuture<AuthResult> changePassword(UUID playerUuid, String oldPassword, String newPassword);
    CompletableFuture<Boolean> isRegistered(UUID playerUuid);
    
    enum AuthResult {
        SUCCESS,
        ALREADY_REGISTERED,
        NOT_REGISTERED,
        WRONG_PASSWORD,
        DATABASE_ERROR,
        INVALID_PASSWORD
    }
}
