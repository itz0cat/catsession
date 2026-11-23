package com.catsession.gui;

import com.catsession.auth.AuthProfile;
import com.catsession.manager.SessionManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Screen allowing manual entry of user-authorized offline / local development profiles.
 */
public class ManualImportScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget usernameField;
    private TextFieldWidget uuidField;
    private String feedbackMessage = "";

    public ManualImportScreen(Screen parent) {
        super(Text.literal("Import Authorized Offline Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.usernameField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY - 45, 200, 20, Text.literal("Username"));
        this.usernameField.setMaxLength(16);
        this.addDrawableChild(this.usernameField);

        this.uuidField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY + 5, 200, 20, Text.literal("UUID (Optional)"));
        this.uuidField.setMaxLength(36);
        this.addDrawableChild(this.uuidField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Profile"), button -> {
            String username = usernameField.getText().trim();
            if (username.isEmpty()) {
                this.feedbackMessage = "Username cannot be empty!";
                return;
            }

            UUID customUuid = null;
            String rawUuid = uuidField.getText().trim();
            if (!rawUuid.isEmpty()) {
                try {
                    customUuid = UUID.fromString(rawUuid);
                } catch (IllegalArgumentException e) {
                    this.feedbackMessage = "Invalid UUID format!";
                    return;
                }
            }

            AuthProfile profile = AuthProfile.createOffline(username, customUuid);
            SessionManager.getInstance().addProfile(profile);

            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(centerX - 105, centerY + 45, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(centerX + 5, centerY + 45, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 80, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Username:"), centerX - 100, centerY - 58, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Custom UUID (Leave blank for default):"), centerX - 100, centerY - 8, 0xAAAAAA);

        if (!feedbackMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(feedbackMessage), centerX, centerY + 75, 0xFF5555);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
