package com.catsession.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Safety confirmation screen to prevent accidental deletion of sessions or wiping all data.
 */
public class ConfirmActionScreen extends Screen {

    private final Screen parent;
    private final Text message;
    private final Runnable onConfirm;

    public ConfirmActionScreen(Screen parent, Text title, Text message, Runnable onConfirm) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), button -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(centerX - 105, centerY + 30, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(centerX + 5, centerY + 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 40, 0xFF5555);
        context.drawCenteredTextWithShadow(this.textRenderer, this.message, centerX, centerY - 15, 0xDDDDDD);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
