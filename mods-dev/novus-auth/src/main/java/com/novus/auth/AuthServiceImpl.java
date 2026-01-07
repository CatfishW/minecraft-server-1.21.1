package com.novus.auth;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AuthServiceImpl implements AuthService {
    private final SQLiteAuthStorage storage;
    private final BcryptHasher hasher;

    public AuthServiceImpl(SQLiteAuthStorage storage, BcryptHasher hasher) {
        this.storage = storage;
        this.hasher = hasher;
    }

    @Override
    public CompletableFuture<AuthResult> register(UUID playerUuid, String username, String password) {
        return storage.exists(playerUuid).thenCompose(exists -> {
            if (exists) return CompletableFuture.completedFuture(AuthResult.ALREADY_REGISTERED);
            if (password.length() < 6) return CompletableFuture.completedFuture(AuthResult.INVALID_PASSWORD);
            
            String hash = hasher.hash(password);
            return storage.saveUser(playerUuid, username, hash)
                    .thenApply(success -> success ? AuthResult.SUCCESS : AuthResult.DATABASE_ERROR);
        });
    }

    @Override
    public CompletableFuture<AuthResult> login(UUID playerUuid, String password) {
        return storage.getPasswordHash(playerUuid).thenApply(hash -> {
            if (hash == null) return AuthResult.NOT_REGISTERED;
            if (hasher.verify(password, hash)) {
                return AuthResult.SUCCESS;
            }
            return AuthResult.WRONG_PASSWORD;
        });
    }

    @Override
    public CompletableFuture<AuthResult> changePassword(UUID playerUuid, String oldPassword, String newPassword) {
        return storage.getPasswordHash(playerUuid).thenCompose(hash -> {
            if (hash == null) return CompletableFuture.completedFuture(AuthResult.NOT_REGISTERED);
            if (!hasher.verify(oldPassword, hash)) return CompletableFuture.completedFuture(AuthResult.WRONG_PASSWORD);
            if (newPassword.length() < 6) return CompletableFuture.completedFuture(AuthResult.INVALID_PASSWORD);

            String newHash = hasher.hash(newPassword);
            return storage.updatePassword(playerUuid, newHash)
                    .thenApply(success -> success ? AuthResult.SUCCESS : AuthResult.DATABASE_ERROR);
        });
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(UUID playerUuid) {
        return storage.exists(playerUuid);
    }
}
