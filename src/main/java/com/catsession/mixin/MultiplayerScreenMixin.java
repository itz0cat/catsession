package com.catsession.mixin;

import com.catsession.gui.CatSessionScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addCatSessionButton(CallbackInfo ci) {
        // Place button in the top-right area of multiplayer server list
        this.addDrawableChild(ButtonWidget.builder(Text.literal("CatSession"), button -> {
            if (this.client != null) {
                this.client.setScreen(new CatSessionScreen(this));
            }
        }).dimensions(this.width - 85, 8, 75, 20).build());
    }
}
