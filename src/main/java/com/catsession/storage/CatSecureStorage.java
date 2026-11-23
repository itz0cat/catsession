package com.catsession.storage;

import com.catsession.auth.AuthProfile;
import com.catsession.util.CatSessionLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/**
 * OS-backed and AES-256-GCM encrypted local storage for authorized Minecraft sessions.
 * Never stores tokens in plaintext and restricts OS-level file permissions.
 */
public class CatSecureStorage {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PROFILE_LIST_TYPE = new TypeToken<List<AuthProfile>>() {}.getType();

    private final Path storageDir;
    private final Path sessionsFile;
    private final Path keySaltFile;
    private final SecureRandom secureRandom;

    public CatSecureStorage() {
        this.storageDir = FabricLoader.getInstance().getConfigDir().resolve("catsession");
        this.sessionsFile = storageDir.resolve("sessions.enc");
        this.keySaltFile = storageDir.resolve(".key");
        this.secureRandom = new SecureRandom();
        ensureDirectory();
    }

    private void ensureDirectory() {
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
                applyStrictPermissions(storageDir);
            }
        } catch (Exception e) {
            CatSessionLogger.error("Failed to initialize secure storage directory: " + e.getMessage());
        }
    }

    private void applyStrictPermissions(Path path) {
        try {
            if (File.separatorChar == '/') {
                Set<PosixFilePermission> perms = Files.isDirectory(path)
                        ? PosixFilePermissions.fromString("rwx------")
                        : PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(path, perms);
            }
        } catch (Exception ignored) {
            // Non-posix OS or restricted environment
        }
    }

    private SecretKey deriveOrLoadKey() throws Exception {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        if (Files.exists(keySaltFile)) {
            byte[] existing = Files.readAllBytes(keySaltFile);
            if (existing.length >= SALT_LENGTH_BYTES) {
                System.arraycopy(existing, 0, salt, 0, SALT_LENGTH_BYTES);
            } else {
                secureRandom.nextBytes(salt);
                Files.write(keySaltFile, salt);
                applyStrictPermissions(keySaltFile);
            }
        } else {
            secureRandom.nextBytes(salt);
            Files.write(keySaltFile, salt);
            applyStrictPermissions(keySaltFile);
        }

        // Anchor with OS user, machine info, and local salt
        String machineAnchor = System.getProperty("user.name", "mc-user")
                + ":" + System.getProperty("os.name", "unknown")
                + ":" + System.getProperty("user.home", "");

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(machineAnchor.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public synchronized void saveSessions(List<AuthProfile> profiles) {
        try {
            ensureDirectory();
            SecretKey key = deriveOrLoadKey();
            String json = GSON.toJson(profiles != null ? profiles : Collections.emptyList());
            byte[] plaintext = json.getBytes(StandardCharsets.UTF_8);

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Payload structure: [12-byte IV] + [Ciphertext + Tag]
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

            Path tempFile = storageDir.resolve("sessions.tmp");
            Files.write(tempFile, payload);
            applyStrictPermissions(tempFile);
            Files.move(tempFile, sessionsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            applyStrictPermissions(sessionsFile);

            CatSessionLogger.info("Securely saved " + (profiles != null ? profiles.size() : 0) + " authorized sessions.");
        } catch (Exception e) {
            CatSessionLogger.error("Failed to encrypt and save sessions: " + e.getMessage());
        }
    }

    public synchronized List<AuthProfile> loadSessions() {
        if (!Files.exists(sessionsFile)) {
            return new ArrayList<>();
        }

        try {
            byte[] payload = Files.readAllBytes(sessionsFile);
            if (payload.length <= GCM_IV_LENGTH_BYTES) {
                CatSessionLogger.warn("Stored session file is invalid or empty.");
                return new ArrayList<>();
            }

            SecretKey key = deriveOrLoadKey();
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH_BYTES);

            int cipherLength = payload.length - GCM_IV_LENGTH_BYTES;
            byte[] ciphertext = new byte[cipherLength];
            System.arraycopy(payload, GCM_IV_LENGTH_BYTES, ciphertext, 0, cipherLength);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            byte[] plaintext = cipher.doFinal(ciphertext);

            String json = new String(plaintext, StandardCharsets.UTF_8);
            List<AuthProfile> list = GSON.fromJson(json, PROFILE_LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            CatSessionLogger.error("Failed to decrypt local sessions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized void clearAllData() {
        try {
            secureWipeFile(sessionsFile);
            secureWipeFile(keySaltFile);
            CatSessionLogger.info("All local CatSession data has been securely wiped.");
        } catch (Exception e) {
            CatSessionLogger.error("Error wiping CatSession data: " + e.getMessage());
        }
    }

    private void secureWipeFile(Path path) {
        try {
            if (Files.exists(path)) {
                long length = Files.size(path);
                try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rws")) {
                    byte[] zeros = new byte[(int) Math.min(length, 4096)];
                    long written = 0;
                    while (written < length) {
                        int chunk = (int) Math.min(zeros.length, length - written);
                        raf.write(zeros, 0, chunk);
                        written += chunk;
                    }
                }
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignoredInner) {}
        }
    }
}
