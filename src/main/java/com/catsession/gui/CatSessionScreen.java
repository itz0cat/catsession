package com.catsession.gui;

import com.catsession.auth.AuthProfile;
import com.catsession.manager.SessionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Main In-Game GUI for CatSession Manager.
 * Displays the current session securely, lists authorized profiles,
 * and allows explicit session switching, deletion, and data clearance.
 */
public class CatSessionScreen extends Screen {

    private final Screen parent;
    private int selectedIndex = -1;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 4;
    private String statusFeedback = "";
    private int feedbackColor = 0x55FF55;

    public CatSessionScreen(Screen parent) {
        super(Text.literal("CatSession Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshButtons();
    }

    private void refreshButtons() {
        this.clearChildren();

        int centerX = this.width / 2;
        int bottomY = this.height - 58;

        List<AuthProfile> profiles = SessionManager.getInstance().getProfiles();
        int maxPages = Math.max(1, (int) Math.ceil((double) profiles.size() / ITEMS_PER_PAGE));
        if (page >= maxPages) {
            page = Math.max(0, maxPages - 1);
        }

        // Pagination buttons
        if (maxPages > 1) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("◀ Prev"), button -> {
                if (page > 0) {
                    page--;
                    selectedIndex = -1;
                    refreshButtons();
                }
            }).dimensions(centerX - 180, 160, 50, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Next ▶"), button -> {
                if (page < maxPages - 1) {
                    page++;
                    selectedIndex = -1;
                    refreshButtons();
                }
            }).dimensions(centerX + 130, 160, 50, 20).build());
        }

        // Selection buttons for visible items
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, profiles.size());
        for (int i = startIndex; i < endIndex; i++) {
            final int itemIndex = i;
            int cardY = 80 + (i - startIndex) * 24;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Select"), button -> {
                selectedIndex = itemIndex;
                refreshButtons();
            }).dimensions(centerX + 110, cardY, 50, 18).build());
        }

        // Action Buttons - Row 1
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Switch Session"), button -> {
            if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
                AuthProfile profile = profiles.get(selectedIndex);
                boolean success = SessionManager.getInstance().switchSession(profile);
                if (success) {
                    statusFeedback = "Switched session to " + profile.getUsername();
                    feedbackColor = 0x55FF55;
                } else {
                    statusFeedback = "Failed to switch session.";
                    feedbackColor = 0xFF5555;
                }
                refreshButtons();
            } else {
                statusFeedback = "Please select a profile first.";
                feedbackColor = 0xFFAA00;
            }
        }).dimensions(centerX - 180, bottomY, 115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Login via MS"), button -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceCodeModalScreen(this));
            }
        }).dimensions(centerX - 60, bottomY, 115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Import Offline"), button -> {
            if (this.client != null) {
                this.client.setScreen(new ManualImportScreen(this));
            }
        }).dimensions(centerX + 60, bottomY, 120, 20).build());

        // Action Buttons - Row 2
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete Session"), button -> {
            if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
                AuthProfile profile = profiles.get(selectedIndex);
                if (this.client != null) {
                    this.client.setScreen(new ConfirmActionScreen(
                            this,
                            Text.literal("Delete Authorized Session"),
                            Text.literal("Delete " + profile.getUsername() + " from encrypted storage?"),
                            () -> {
                                SessionManager.getInstance().deleteProfile(profile.getId());
                                selectedIndex = -1;
                                statusFeedback = "Session deleted.";
                                feedbackColor = 0x55FF55;
                            }
                    ));
                }
            } else {
                statusFeedback = "Select a profile to delete.";
                feedbackColor = 0xFFAA00;
            }
        }).dimensions(centerX - 180, bottomY + 25, 115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear All Data"), button -> {
            if (this.client != null) {
                this.client.setScreen(new ConfirmActionScreen(
                        this,
                        Text.literal("Clear All Local Data"),
                        Text.literal("Wipe all stored sessions and encryption keys? This cannot be undone."),
                        () -> {
                            SessionManager.getInstance().clearAllData();
                            selectedIndex = -1;
                            page = 0;
                            statusFeedback = "All data cleared.";
                            feedbackColor = 0x55FF55;
                        }
                ));
            }
        }).dimensions(centerX - 60, bottomY + 25, 115, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> {
            close();
        }).dimensions(centerX + 60, bottomY + 25, 120, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;

        // Title Header
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("CatSession Manager"), centerX, 10, 0x55FFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Local-Only Authorized Session Utility"), centerX, 22, 0x888888);

        // Active Minecraft Session Box
        MinecraftClient client = MinecraftClient.getInstance();
        Session activeSession = client.getSession();
        String activeUser = activeSession != null ? activeSession.getUsername() : "Unknown";
        String activeType = activeSession != null ? activeSession.getAccountType().name() : "N/A";
        String uuidStr = (activeSession != null && activeSession.getUuidOrNull() != null)
                ? activeSession.getUuidOrNull().toString()
                : "00000000-0000-0000-0000-000000000000";

        // Draw Active Session Card Header
        context.fill(centerX - 185, 36, centerX + 185, 68, 0x80222222);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Active Session: " + activeUser + " [" + activeType + "]"), centerX - 180, 40, 0x55FF55);
        context.drawTextWithShadow(this.textRenderer, Text.literal("UUID: " + uuidStr), centerX - 180, 52, 0xAAAAAA);

        // Profiles List Section Header
        List<AuthProfile> profiles = SessionManager.getInstance().getProfiles();
        int maxPages = Math.max(1, (int) Math.ceil((double) profiles.size() / ITEMS_PER_PAGE));
        context.drawTextWithShadow(this.textRenderer, Text.literal("Saved Authorized Sessions (" + profiles.size() + ") - Page " + (page + 1) + "/" + maxPages + ":"), centerX - 180, 72, 0xFFFF55);

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, profiles.size());

        if (profiles.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No authorized sessions saved. Click 'Login via MS' or 'Import Offline'."), centerX, 100, 0x777777);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (int i = startIndex; i < endIndex; i++) {
                AuthProfile p = profiles.get(i);
                int cardY = 80 + (i - startIndex) * 24;
                boolean isSelected = (i == selectedIndex);

                int bgColor = isSelected ? 0x90335588 : 0x60111111;
                context.fill(centerX - 185, cardY, centerX + 185, cardY + 22, bgColor);

                String badge = "[" + p.getAccountType().toUpperCase() + "]";
                int badgeColor = "msa".equalsIgnoreCase(p.getAccountType()) ? 0x55FF55 : 0xAAAAAA;

                String rowText = (isSelected ? "▶ " : "  ") + p.getUsername() + " " + badge;
                context.drawTextWithShadow(this.textRenderer, Text.literal(rowText), centerX - 180, cardY + 2, isSelected ? 0xFFFFAA : 0xFFFFFF);

                String dateStr = "Used: " + sdf.format(new Date(p.getLastUsedAt()));
                context.drawTextWithShadow(this.textRenderer, Text.literal(dateStr), centerX - 180, cardY + 12, 0x888888);
            }
        }

        // Status Feedback banner
        if (!statusFeedback.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusFeedback), centerX, this.height - 72, feedbackColor);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
