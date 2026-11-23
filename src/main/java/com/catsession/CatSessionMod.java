package com.catsession;

import com.catsession.gui.CatSessionScreen;
import com.catsession.manager.SessionManager;
import com.catsession.util.CatSessionLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CatSessionMod implements ClientModInitializer {

    public static final String MOD_ID = "catsession";
    private static KeyBinding sessionMenuKey;

    @Override
    public void onInitializeClient() {
        CatSessionLogger.info("Initializing CatSession Manager (Fabric 1.21.11)...");

        // Preload encrypted sessions into memory
        SessionManager.getInstance();

        // Register in-game shortcut KeyBinding (Default: M)
        sessionMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.catsession.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.catsession"
        ));

        // Listen for key presses to open the GUI
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (sessionMenuKey.wasPressed()) {
                if (client.currentScreen == null || !(client.currentScreen instanceof CatSessionScreen)) {
                    client.setScreen(new CatSessionScreen(client.currentScreen));
                }
            }
        });

        CatSessionLogger.info("CatSession Manager initialized successfully.");
    }
}
