package com.catsession.auth;

import com.catsession.util.CatSanitizer;
import com.catsession.util.CatSessionLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standard RFC 8628 OAuth 2.0 Device Code Authorization Flow for Microsoft / Minecraft.
 * Directly communicates with official Microsoft & Mojang endpoints only.
 * No telemetry, no third-party proxies, and respects user 2FA and account authorization.
 */
public class MicrosoftDeviceFlow {

    // Standard client ID registered for Minecraft ecosystem client authorization
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "XboxLive.signin offline_access";

    private final AtomicBoolean running = new AtomicBoolean(false);

    public interface Callback {
        void onDeviceCodeReceived(String userCode, String verificationUrl, int expiresInSeconds);
        void onStatusUpdate(String statusMessage);
        void onSuccess(AuthProfile profile);
        void onError(String errorMessage);
    }

    public void start(Callback callback) {
        if (running.getAndSet(true)) {
            callback.onError("Authentication is already in progress.");
            return;
        }

        Thread authThread = new Thread(() -> {
            try {
                callback.onStatusUpdate("Requesting device code from Microsoft...");

                // Step 1: Request Device Code from official Microsoft endpoint
                String postParams = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                        + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);

                JsonObject deviceCodeResp = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", postParams);
                if (deviceCodeResp == null || !deviceCodeResp.has("device_code")) {
                    callback.onError("Failed to obtain device code from Microsoft.");
                    running.set(false);
                    return;
                }

                String deviceCode = deviceCodeResp.get("device_code").getAsString();
                String userCode = deviceCodeResp.get("user_code").getAsString();
                String verificationUri = deviceCodeResp.has("verification_uri")
                        ? deviceCodeResp.get("verification_uri").getAsString()
                        : "https://microsoft.com/link";
                int interval = deviceCodeResp.has("interval") ? deviceCodeResp.get("interval").getAsInt() : 5;
                int expiresIn = deviceCodeResp.has("expires_in") ? deviceCodeResp.get("expires_in").getAsInt() : 900;

                callback.onDeviceCodeReceived(userCode, verificationUri, expiresIn);
                callback.onStatusUpdate("Waiting for authorization on " + verificationUri);

                // Step 2: Poll for user authorization
                long startTime = System.currentTimeMillis();
                long timeoutMillis = expiresIn * 1000L;
                String msAccessToken = null;
                String msRefreshToken = null;

                while (running.get() && (System.currentTimeMillis() - startTime) < timeoutMillis) {
                    Thread.sleep(Math.max(interval, 5) * 1000L);
                    if (!running.get()) break;

                    String pollParams = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:device_code", StandardCharsets.UTF_8)
                            + "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                            + "&device_code=" + URLEncoder.encode(deviceCode, StandardCharsets.UTF_8);

                    JsonObject tokenResp = postFormNoThrow("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", pollParams);
                    if (tokenResp != null) {
                        if (tokenResp.has("access_token")) {
                            msAccessToken = tokenResp.get("access_token").getAsString();
                            if (tokenResp.has("refresh_token")) {
                                msRefreshToken = tokenResp.get("refresh_token").getAsString();
                            }
                            break;
                        } else if (tokenResp.has("error")) {
                            String err = tokenResp.get("error").getAsString();
                            if ("authorization_pending".equals(err)) {
                                // Still waiting for user to click approve in browser
                                continue;
                            } else if ("slow_down".equals(err)) {
                                interval += 5;
                            } else {
                                callback.onError("Authorization declined or error: " + err);
                                running.set(false);
                                return;
                            }
                        }
                    }
                }

                if (!running.get()) {
                    callback.onStatusUpdate("Authentication cancelled.");
                    return;
                }

                if (msAccessToken == null) {
                    callback.onError("Authentication timed out waiting for approval.");
                    running.set(false);
                    return;
                }

                // Step 3: Xbox Live Authentication
                callback.onStatusUpdate("Authenticating with Xbox Live...");
                JsonObject xblReq = new JsonObject();
                JsonObject xblProps = new JsonObject();
                xblProps.addProperty("AuthMethod", "RPS");
                xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
                xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
                xblReq.add("Properties", xblProps);
                xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
                xblReq.addProperty("TokenType", "JWT");

                JsonObject xblResp = postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq);
                if (xblResp == null || !xblResp.has("Token")) {
                    callback.onError("Xbox Live authentication failed.");
                    running.set(false);
                    return;
                }
                String xblToken = xblResp.get("Token").getAsString();
                String uhs = xblResp.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

                // Step 4: XSTS Token for Minecraft Services
                callback.onStatusUpdate("Obtaining XSTS Security Token...");
                JsonObject xstsReq = new JsonObject();
                JsonObject xstsProps = new JsonObject();
                xstsProps.addProperty("SandboxId", "RETAIL");
                JsonArray userTokens = new JsonArray();
                userTokens.add(xblToken);
                xstsProps.add("UserTokens", userTokens);
                xstsReq.add("Properties", xstsProps);
                xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                xstsReq.addProperty("TokenType", "JWT");

                JsonObject xstsResp = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq);
                if (xstsResp == null || !xstsResp.has("Token")) {
                    callback.onError("XSTS authorization failed. Check if Xbox profile is active.");
                    running.set(false);
                    return;
                }
                String xstsToken = xstsResp.get("Token").getAsString();

                // Step 5: Exchange XSTS with Minecraft Services
                callback.onStatusUpdate("Logging into Minecraft Services...");
                JsonObject mcReq = new JsonObject();
                mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

                JsonObject mcResp = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq);
                if (mcResp == null || !mcResp.has("access_token")) {
                    callback.onError("Minecraft authentication failed.");
                    running.set(false);
                    return;
                }
                String mcAccessToken = mcResp.get("access_token").getAsString();
                long mcExpiresIn = mcResp.has("expires_in") ? mcResp.get("expires_in").getAsLong() : 86400L;
                long expiresAt = System.currentTimeMillis() + (mcExpiresIn * 1000L);

                // Step 6: Fetch Profile Details (Username & UUID)
                callback.onStatusUpdate("Retrieving Minecraft Profile...");
                JsonObject profileResp = getJsonWithAuth("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
                if (profileResp == null || !profileResp.has("name") || !profileResp.has("id")) {
                    callback.onError("No active Minecraft Java license found on this Microsoft account.");
                    running.set(false);
                    return;
                }

                String username = profileResp.get("name").getAsString();
                String rawUuid = profileResp.get("id").getAsString();
                UUID uuid = parseDashlessUuid(rawUuid);

                AuthProfile profile = new AuthProfile(
                        UUID.randomUUID().toString(),
                        username,
                        uuid,
                        mcAccessToken,
                        msRefreshToken,
                        expiresAt,
                        "msa",
                        uhs,
                        CLIENT_ID,
                        System.currentTimeMillis(),
                        System.currentTimeMillis()
                );

                CatSessionLogger.info("Successfully authenticated authorized session for player: " + username);
                callback.onSuccess(profile);
            } catch (InterruptedException ie) {
                callback.onStatusUpdate("Authentication was cancelled.");
            } catch (Exception e) {
                CatSessionLogger.error("Device code flow exception: " + e.getMessage());
                callback.onError("Authentication error: " + CatSanitizer.sanitize(e.getMessage()));
            } finally {
                running.set(false);
            }
        }, "CatSession-OAuth-Worker");

        authThread.setDaemon(true);
        authThread.start();
    }

    public void cancel() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    private static UUID parseDashlessUuid(String raw) {
        if (raw == null) return UUID.randomUUID();
        String formatted = raw.length() == 32
                ? raw.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5")
                : raw;
        try {
            return UUID.fromString(formatted);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }

    private static JsonObject postForm(String endpoint, String body) throws Exception {
        URL url = URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject postFormNoThrow(String endpoint, String body) {
        try {
            return postForm(endpoint, body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject postJson(String endpoint, JsonObject jsonBody) throws Exception {
        URL url = URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject getJsonWithAuth(String endpoint, String token) throws Exception {
        URL url = URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
