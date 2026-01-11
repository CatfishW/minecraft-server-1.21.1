package com.flaneconomy.service;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

public class FlanClaimService {
    private final MinecraftServer server;
    private final boolean available;
    private final Method getPlayerData;
    private final Method remainingClaimBlocks;
    private final Method getAdditionalClaims;
    private final Method setAdditionalClaims;
    private final Method getClaimStorage;
    private final Method getClaimAt;
    private final Method getFromUuid;
    private final Method getClaimId;
    private final Method getClaimName;
    private final Method setClaimName;
    private final Method getClaimOwner;
    private final Method transferOwner;
    private final Method getHomePos;
    private final Method getWorld;
    private final Method getClaims;

    public FlanClaimService(MinecraftServer server) {
        this.server = server;
        Method playerDataMethod = null;
        Method remainingClaimBlocksMethod = null;
        Method getAdditionalClaimsMethod = null;
        Method setAdditionalClaimsMethod = null;
        Method getClaimStorageMethod = null;
        Method getClaimAtMethod = null;
        Method getFromUuidMethod = null;
        Method getClaimIdMethod = null;
        Method getClaimNameMethod = null;
        Method setClaimNameMethod = null;
        Method getOwnerMethod = null;
        Method transferOwnerMethod = null;
        Method getHomePosMethod = null;
        Method getWorldMethod = null;
        Method getClaimsMethod = null;
        boolean ok = false;
        try {
            Class<?> claimHandlerClass = Class.forName("io.github.flemmli97.flan.api.ClaimHandler");
            Class<?> playerDataClass = Class.forName("io.github.flemmli97.flan.api.data.IPlayerData");
            Class<?> claimStorageClass = Class.forName("io.github.flemmli97.flan.claim.ClaimStorage");
            Class<?> claimClass = Class.forName("io.github.flemmli97.flan.claim.Claim");

            playerDataMethod = claimHandlerClass.getMethod("getPlayerData", ServerPlayer.class);
            remainingClaimBlocksMethod = playerDataClass.getMethod("remainingClaimBlocks");
            getAdditionalClaimsMethod = playerDataClass.getMethod("getAdditionalClaims");
            setAdditionalClaimsMethod = playerDataClass.getMethod("setAdditionalClaims", int.class);

            getClaimStorageMethod = claimStorageClass.getMethod("get", ServerLevel.class);
            getClaimAtMethod = claimStorageClass.getMethod("getClaimAt", BlockPos.class);
            getFromUuidMethod = claimStorageClass.getMethod("getFromUUID", UUID.class);

            getClaimIdMethod = claimClass.getMethod("getClaimID");
            getClaimNameMethod = claimClass.getMethod("getClaimName");
            setClaimNameMethod = claimClass.getMethod("setClaimName", String.class);
            getOwnerMethod = claimClass.getMethod("getOwner");
            transferOwnerMethod = claimClass.getMethod("transferOwner", UUID.class);
            getHomePosMethod = claimClass.getMethod("getHomePos");
            getWorldMethod = claimClass.getMethod("getLevel");
            getClaimsMethod = claimStorageClass.getMethod("getClaims");

            ok = true;
        } catch (Exception e) {
            ok = false;
        }
        this.available = ok;
        this.getPlayerData = playerDataMethod;
        this.remainingClaimBlocks = remainingClaimBlocksMethod;
        this.getAdditionalClaims = getAdditionalClaimsMethod;
        this.setAdditionalClaims = setAdditionalClaimsMethod;
        this.getClaimStorage = getClaimStorageMethod;
        this.getClaimAt = getClaimAtMethod;
        this.getFromUuid = getFromUuidMethod;
        this.getClaimId = getClaimIdMethod;
        this.getClaimName = getClaimNameMethod;
        this.setClaimName = setClaimNameMethod;
        this.getClaimOwner = getOwnerMethod;
        this.transferOwner = transferOwnerMethod;
        this.getHomePos = getHomePosMethod;
        this.getWorld = getWorldMethod;
        this.getClaims = getClaimsMethod;
    }

    public boolean isAvailable() {
        return available;
    }

    public int getClaimBlocks(ServerPlayer player) {
        if (!available || player == null) {
            return 0;
        }
        try {
            Object playerData = getPlayerData.invoke(null, player);
            return (int) remainingClaimBlocks.invoke(playerData);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean addClaimBlocks(ServerPlayer player, int amount) {
        if (!available || player == null || amount <= 0) {
            return false;
        }
        try {
            Object playerData = getPlayerData.invoke(null, player);
            int currentAdditional = (int) getAdditionalClaims.invoke(playerData);
            setAdditionalClaims.invoke(playerData, currentAdditional + amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Object getClaimAt(ServerPlayer player) {
        if (!available || player == null) {
            return null;
        }
        try {
            Object storage = getClaimStorage.invoke(null, player.serverLevel());
            return getClaimAt.invoke(storage, player.blockPosition());
        } catch (Exception e) {
            return null;
        }
    }

    public Object getClaimById(UUID claimId) {
        if (!available || claimId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            try {
                Object storage = getClaimStorage.invoke(null, level);
                Object claim = getFromUuid.invoke(storage, claimId);
                if (claim != null) {
                    return claim;
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public UUID getClaimId(Object claim) {
        if (!available || claim == null) {
            return null;
        }
        try {
            return (UUID) getClaimId.invoke(claim);
        } catch (Exception e) {
            return null;
        }
    }

    public String getClaimName(Object claim) {
        if (!available || claim == null) {
            return "";
        }
        try {
            Object name = getClaimName.invoke(claim);
            return name == null ? "" : name.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public UUID getClaimOwner(Object claim) {
        if (!available || claim == null) {
            return null;
        }
        try {
            return (UUID) getClaimOwner.invoke(claim);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean setClaimName(Object claim, String name) {
        if (!available || claim == null || name == null) {
            return false;
        }
        try {
            setClaimName.invoke(claim, name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean transferClaim(Object claim, UUID newOwner) {
        if (!available || claim == null || newOwner == null) {
            return false;
        }
        try {
            transferOwner.invoke(claim, newOwner);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean teleportToClaim(ServerPlayer player, Object claim) {
        if (!available || player == null || claim == null) {
            return false;
        }
        try {
            BlockPos home = (BlockPos) getHomePos.invoke(claim);
            ServerLevel level = (ServerLevel) getWorld.invoke(claim);
            if (home != null && level != null) {
                player.teleportTo(level, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, player.getYRot(), player.getXRot());
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public java.util.Collection<Object> getAllClaims() {
        if (!available) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Object> all = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            try {
                Object storage = getClaimStorage.invoke(null, level);
                java.util.Map<?, java.util.Set<?>> claimsMap = (java.util.Map<?, java.util.Set<?>>) getClaims.invoke(storage);
                if (claimsMap != null) {
                    for (java.util.Set<?> set : claimsMap.values()) {
                        all.addAll(set);
                    }
                }
            } catch (Exception ignored) {}
        }
        return all;
    }
}
