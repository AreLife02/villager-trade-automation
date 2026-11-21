package com.example.farmerautomation;

import com.example.farmerautomation.screen.AutomationScreen;
import com.example.farmerautomation.screen.VillagerManagementScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class MainMod implements ClientModInitializer {
    public static final String MOD_ID = "farmerautomation";

    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        AutomationConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "controls")
        );

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.farmerautomation.open_menu",
                GLFW.GLFW_KEY_O,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                Minecraft.getInstance().setScreen(new VillagerManagementScreen(new AutomationScreen()));
            }

            AutomationManager.INSTANCE.tick(client);
        });

        ErrorHandler.info("Farmer Automation yüklendi. Kontrol paneli için O tuşuna bas.");
    }
}
