package com.catsession.auth;

import com.catsession.util.CatSanitizer;
import net.minecraft.client.session.Session;

import java.util.Optional;
import java.util.UUID;

/**
 * Model representing an explicitly authorized user profile/session.
 */
public class AuthProfile {

    private final String id;
    private final String username;
    private final UUID uuid;
    private final String accessToken;
    private final String refreshToken;
    private final long expiresAt;
    private final String accountType; // "msa", "mojang", "legacy"
    private final String xuid;
    private final String clientId;
    private final long createdAt;
    private long lastUsedAt;

    public AuthProfile(String id, String username, UUID uuid, String accessToken, String refreshToken,
                       long expiresAt, String accountType, String xuid, String clientId,
                       long createdAt, long lastUsedAt) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.username = username != null ? username : "Player";
        this.uuid = uuid != null ? uuid : UUID.randomUUID();
        this.accessToken = accessToken != null ? accessToken : "";
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.accountType = accountType != null ? accountType.toLowerCase() : "msa";
        this.xuid = xuid;
        this.clientId = clientId;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.lastUsedAt = lastUsedAt > 0 ? lastUsedAt : this.createdAt;
    }

    public static AuthProfile createOffline(String username, UUID uuid) {
        UUID effectiveUuid = uuid != null ? uuid : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
        return new AuthProfile(
                UUID.randomUUID().toString(),
                username,
                effectiveUuid,
                "offline-token",
                null,
                0L,
                "legacy",
                null,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        if (expiresAt <= 0) return false; // Offline / permanent
        return System.currentTimeMillis() > expiresAt;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getXuid() {
        return xuid;
    }

    public String getClientId() {
        return clientId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public void updateLastUsed() {
        this.lastUsedAt = System.currentTimeMillis();
    }

    public Session.AccountType getMojangAccountType() {
        if ("msa".equalsIgnoreCase(accountType)) {
            return Session.AccountType.MSA;
        } else if ("mojang".equalsIgnoreCase(accountType)) {
            return Session.AccountType.MOJANG;
        } else {
            return Session.AccountType.LEGACY;
        }
    }

    public Session toMinecraftSession() {
        return new Session(
                this.username,
                this.uuid,
                this.accessToken,
                Optional.ofNullable(this.xuid),
                Optional.ofNullable(this.clientId),
                getMojangAccountType()
        );
    }

    @Override
    public String toString() {
        return "AuthProfile{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", uuid=" + uuid +
                ", accountType='" + accountType + '\'' +
                ", token=" + CatSanitizer.maskToken(accessToken) +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
