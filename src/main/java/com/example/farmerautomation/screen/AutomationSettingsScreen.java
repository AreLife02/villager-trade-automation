package com.example.farmerautomation.screen;

import com.example.farmerautomation.AutomationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AutomationSettingsScreen extends Screen {
    private final Screen parent;

    public AutomationSettingsScreen(Screen parent) {
        super(Component.literal("Otomasyon Ayarları"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AutomationConfig config = AutomationConfig.get();
        int center = width / 2;
        int y = 55;

        addRenderableWidget(Button.builder(Component.literal("Tarama mesafesi: −16"),
                b -> changeRadius(-16)).bounds(center - 110, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Tarama mesafesi: +16"),
                b -> changeRadius(16)).bounds(center + 5, y, 105, 20).build());

        y += 25;
        addRenderableWidget(Button.builder(radiusLabel(config), b -> { }).bounds(center - 110, y, 220, 20).build());

        y += 30;
        addRenderableWidget(Button.builder(Component.literal("Depo arama mesafesi: −1"),
                b -> changeStorageRadius(-1)).bounds(center - 110, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Depo arama mesafesi: +1"),
                b -> changeStorageRadius(1)).bounds(center + 5, y, 105, 20).build());

        y += 25;
        addRenderableWidget(Button.builder(storageRadiusLabel(config), b -> { })
                .bounds(center - 110, y, 220, 20).build());

        y += 35;
        addRenderableWidget(Button.builder(Component.literal("Geri"),
                b -> Minecraft.getInstance().setScreen(parent))
                .bounds(center - 50, height - 28, 100, 20).build());
    }

    private void changeRadius(int delta) {
        AutomationConfig config = AutomationConfig.get();
        config.villagerSearchRadius = Math.max(8, Math.min(256, config.villagerSearchRadius + delta));
        config.save();
        Minecraft.getInstance().setScreen(new AutomationSettingsScreen(parent));
    }

    private void changeStorageRadius(int delta) {
        AutomationConfig config = AutomationConfig.get();
        config.storageSearchRadius = Math.max(1, Math.min(32, config.storageSearchRadius + delta));
        config.save();
        Minecraft.getInstance().setScreen(new AutomationSettingsScreen(parent));
    }

    private static Component radiusLabel(AutomationConfig c) {
        return Component.literal("Mevcut köylü tarama mesafesi: " + c.villagerSearchRadius + " blok");
    }
    private static Component storageRadiusLabel(AutomationConfig c) {
        return Component.literal("Mevcut depo arama mesafesi: " + c.storageSearchRadius + " blok");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render already renders the background. Calling renderBackground here
        // as well triggers Minecraft's "Can only blur once per frame" guard.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Ayarlar otomatik kaydedilir"), width / 2, 39, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
