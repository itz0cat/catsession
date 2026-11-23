package com.catsession.gui;

import com.catsession.auth.AuthProfile;
import com.catsession.manager.SessionManager;
import com.catsession.util.CatSanitizer;
import com.catsession.util.CatSessionLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Screen allowing the account owner to explicitly import and save a Minecraft Bearer token.
 * Validates the token directly with Mojang's official API and saves to AES-256-GCM encrypted storage.
 */
public class TokenImportScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget tokenField;
    private String statusMessage = "Paste your authorized Minecraft access token.";
    private int statusColor = 0xAAAAAA;
    private boolean isWorking = false;

    public TokenImportScreen(Screen parent) {
        super(Text.literal("Import Authorized Access Token"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.tokenField = new TextFieldWidget(this.textRenderer, centerX - 150, centerY - 25, 300, 20, Text.literal("Access Token"));
        this.tokenField.setMaxLength(4096);
        this.addDrawableChild(this.tokenField);

        // Paste from clipboard button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Paste Clipboard"), button -> {
            if (this.client != null) {
                String clipboard = this.client.keyboard.getClipboard();
                if (clipboard != null && !clipboard.trim().isEmpty()) {
                    this.tokenField.setText(clipboard.trim());
                    this.statusMessage = "Token pasted from clipboard.";
                    this.statusColor = 0x55FF55;
                }
            }
        }).dimensions(centerX - 150, centerY + 5, 95, 20).build());

        // Validate and Save button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Validate & Save"), button -> {
            String rawToken = tokenField.getText().trim();
            if (rawToken.isEmpty()) {
                this.statusMessage = "Please enter or paste a token.";
                this.statusColor = 0xFF5555;
                return;
            }

            // Remove Bearer prefix if user copied full header
            final String token = rawToken.toLowerCase().startsWith("bearer ")
                    ? rawToken.substring(7).trim()
                    : rawToken;

            validateAndSaveToken(token);
        }).dimensions(centerX - 50, centerY + 5, 110, 20).build());

        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(centerX + 65, centerY + 5, 85, 20).build());
    }

    private void validateAndSaveToken(String token) {
        if (isWorking) return;
        isWorking = true;
        statusMessage = "Validating token with official Mojang API...";
        statusColor = 0xFFFF55;

        Thread worker = new Thread(() -> {
            try {
                URL url = URI.create("https://api.minecraftservices.com/minecraft/profile").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    JsonObject json;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        json = JsonParser.parseReader(reader).getAsJsonObject();
                    }

                    String name = json.get("name").getAsString();
                    String rawId = json.get("id").getAsString();
                    UUID uuid = parseUuid(rawId);

                    AuthProfile profile = new AuthProfile(
                            UUID.randomUUID().toString(),
                            name,
                            uuid,
                            token,
                            null,
                            System.currentTimeMillis() + (86400L * 1000L), // 24h standard session
                            "msa",
                            null,
                            null,
                            System.currentTimeMillis(),
                            System.currentTimeMillis()
                    );

                    SessionManager.getInstance().addProfile(profile);
                    CatSessionLogger.info("Saved imported token for player: " + name);

                    if (this.client != null) {
                        this.client.execute(() -> {
                            this.client.setScreen(parent);
                        });
                    }
                } else if (code == 401 || code == 403) {
                    if (this.client != null) {
                        this.client.execute(() -> {
                            this.statusMessage = "Invalid or expired token (HTTP " + code + ").";
                            this.statusColor = 0xFF5555;
                            this.isWorking = false;
                        });
                    }
                } else {
                    if (this.client != null) {
                        this.client.execute(() -> {
                            this.statusMessage = "Mojang service responded with HTTP " + code;
                            this.statusColor = 0xFF5555;
                            this.isWorking = false;
                        });
                    }
                }
            } catch (Exception e) {
                CatSessionLogger.error("Failed to validate imported token: " + e.getMessage());
                if (this.client != null) {
                    this.client.execute(() -> {
                        this.statusMessage = "Validation error: " + CatSanitizer.sanitize(e.getMessage());
                        this.statusColor = 0xFF5555;
                        this.isWorking = false;
                    });
                }
            }
        }, "CatSession-Token-Validator");

        worker.setDaemon(true);
        worker.start();
    }

    private static UUID parseUuid(String raw) {
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 65, 0x55FFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Token will be encrypted with AES-256-GCM and saved locally."), centerX, centerY - 50, 0x888888);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Minecraft Access Token:"), centerX - 150, centerY - 38, 0xDDDDDD);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMessage), centerX, centerY + 35, statusColor);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
