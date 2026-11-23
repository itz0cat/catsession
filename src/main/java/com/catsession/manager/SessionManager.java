package com.catsession.manager;

import com.catsession.auth.AuthProfile;
import com.catsession.mixin.MinecraftClientAccessor;
import com.catsession.storage.CatSecureStorage;
import com.catsession.util.CatSessionLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Manages authorized sessions in memory, handles active session switching via mixin accessor,
 * and synchronizes with AES-encrypted local storage.
 * Strictly avoids automatic account switching: changes occur only upon explicit user command.
 */
public class SessionManager {

    private static SessionManager instance;

    private final CatSecureStorage storage;
    private final List<AuthProfile> profiles;

    private SessionManager() {
        this.storage = new CatSecureStorage();
        this.profiles = new ArrayList<>();
        load();
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public synchronized void load() {
        profiles.clear();
        profiles.addAll(storage.loadSessions());
        CatSessionLogger.info("Loaded " + profiles.size() + " authorized profiles from secure storage.");
    }

    public synchronized void save() {
        storage.saveSessions(profiles);
    }

    public synchronized List<AuthProfile> getProfiles() {
        return Collections.unmodifiableList(new ArrayList<>(profiles));
    }

    public synchronized void addProfile(AuthProfile profile) {
        if (profile == null) return;
        // Remove duplicate if same UUID or ID already exists
        profiles.removeIf(p -> p.getUuid().equals(profile.getUuid()) || p.getId().equals(profile.getId()));
        profiles.add(0, profile);
        save();
        CatSessionLogger.info("Added authorized profile: " + profile.getUsername());
    }

    public synchronized boolean deleteProfile(String profileId) {
        if (profileId == null) return false;
        boolean removed = profiles.removeIf(p -> p.getId().equals(profileId));
        if (removed) {
            save();
            CatSessionLogger.info("Deleted authorized session ID: " + profileId);
        }
        return removed;
    }

    public synchronized void clearAllData() {
        profiles.clear();
        storage.clearAllData();
        CatSessionLogger.info("Cleared all session profiles and secure storage.");
    }

    /**
     * Explicitly switches the active Minecraft client session to the requested profile.
     * Never performed automatically; requires explicit user interaction in the GUI.
     */
    public synchronized boolean switchSession(AuthProfile profile) {
        if (profile == null) return false;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Session newSession = profile.toMinecraftSession();

            ((MinecraftClientAccessor) client).setSession(newSession);
            profile.updateLastUsed();
            save();

            CatSessionLogger.info("Explicitly switched active Minecraft session to user: " + profile.getUsername());
            return true;
        } catch (Exception e) {
            CatSessionLogger.error("Failed to switch session: " + e.getMessage());
            return false;
        }
    }

    public synchronized Optional<AuthProfile> findProfileByUuid(String uuidStr) {
        return profiles.stream()
                .filter(p -> p.getUuid().toString().equalsIgnoreCase(uuidStr))
                .findFirst();
    }
}
