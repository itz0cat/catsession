package com.catsession.mixin;

import com.catsession.gui.CatSessionScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addCatSessionButton(CallbackInfo ci) {
        // Place button in the top-right corner of the title screen
        this.addDrawableChild(ButtonWidget.builder(Text.literal("CatSession"), button -> {
            if (this.client != null) {
                this.client.setScreen(new CatSessionScreen(this));
            }
        }).dimensions(this.width - 85, 8, 75, 20).build());
    }
}
