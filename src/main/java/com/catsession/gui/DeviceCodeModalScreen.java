package com.catsession.gui;

import com.catsession.auth.AuthProfile;
import com.catsession.auth.MicrosoftDeviceFlow;
import com.catsession.manager.SessionManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Screen displaying the official Microsoft Device Code Flow verification code and URL.
 * Never collects credentials directly; the user authenticates directly on microsoft.com.
 */
public class DeviceCodeModalScreen extends Screen {

    private final Screen parent;
    private final MicrosoftDeviceFlow flow;

    private String userCode = "Loading...";
    private String verificationUrl = "https://microsoft.com/link";
    private String statusMessage = "Connecting to Microsoft Authentication Service...";
    private boolean isError = false;
    private boolean isFinished = false;

    public DeviceCodeModalScreen(Screen parent) {
        super(Text.literal("Microsoft Device Authorization"));
        this.parent = parent;
        this.flow = new MicrosoftDeviceFlow();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Copy Code & Open Browser"), button -> {
            if (this.client != null && !"Loading...".equals(userCode)) {
                this.client.keyboard.setClipboard(userCode);
                Util.getOperatingSystem().open(verificationUrl);
                this.statusMessage = "Code copied to clipboard! Authorize on Microsoft tab...";
            }
        }).dimensions(centerX - 105, centerY + 30, 210, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            flow.cancel();
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(centerX - 105, centerY + 55, 210, 20).build());

        if (!flow.isRunning() && !isFinished) {
            startFlow();
        }
    }

    private void startFlow() {
        flow.start(new MicrosoftDeviceFlow.Callback() {
            @Override
            public void onDeviceCodeReceived(String code, String url, int expiresInSeconds) {
                userCode = code;
                verificationUrl = url;
                statusMessage = "Enter the code on official Microsoft page.";
            }

            @Override
            public void onStatusUpdate(String status) {
                statusMessage = status;
            }

            @Override
            public void onSuccess(AuthProfile profile) {
                isFinished = true;
                SessionManager.getInstance().addProfile(profile);
                statusMessage = "Authentication successful! Welcome, " + profile.getUsername();
                if (client != null) {
                    client.execute(() -> client.setScreen(parent));
                }
            }

            @Override
            public void onError(String errorMessage) {
                isError = true;
                statusMessage = errorMessage;
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 70, 0x55FF55);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("1. Open: " + verificationUrl), centerX, centerY - 45, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("2. Enter Code:"), centerX, centerY - 30, 0xAAAAAA);

        int codeColor = "Loading...".equals(userCode) ? 0x888888 : 0xFFFF55;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("▶ " + userCode + " ◀"), centerX, centerY - 15, codeColor);

        int statusColor = isError ? 0xFF5555 : (isFinished ? 0x55FF55 : 0xDDDDDD);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMessage), centerX, centerY + 10, statusColor);
    }

    @Override
    public void close() {
        flow.cancel();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
