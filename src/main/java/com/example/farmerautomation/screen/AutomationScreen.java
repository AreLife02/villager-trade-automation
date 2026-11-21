package com.example.farmerautomation.screen;

import com.example.farmerautomation.AutomationConfig;
import com.example.farmerautomation.AutomationManager;
import com.example.farmerautomation.AutomationState;
import com.example.farmerautomation.ErrorHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AutomationScreen extends Screen {
    private final AutomationManager manager = AutomationManager.INSTANCE;

    public AutomationScreen() {
        super(Component.translatable("screen.farmerautomation.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int left = centerX - 105;
        int right = centerX + 5;
        int y = 55;

        this.addRenderableWidget(Button.builder(Component.translatable("screen.farmerautomation.start"),
                button -> manager.start()).bounds(left, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.farmerautomation.stop"),
                button -> manager.stop()).bounds(right, y, 100, 20).build());

        y += 27;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.farmerautomation.pause"),
                button -> manager.pause()).bounds(left, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.farmerautomation.resume"),
                button -> manager.resume()).bounds(right, y, 100, 20).build());

        y += 27;
        this.addRenderableWidget(Button.builder(Component.literal("Köylüleri Yönet"),
                button -> Minecraft.getInstance().setScreen(new VillagerManagementScreen(this)))
                .bounds(centerX - 110, y, 220, 20).build());

        y += 27;
        this.addRenderableWidget(Button.builder(Component.literal("Ayarlar"),
                button -> Minecraft.getInstance().setScreen(new AutomationSettingsScreen(this)))
                .bounds(centerX - 75, y, 150, 20).build());

        AutomationConfig config = AutomationConfig.get();
        y += 27;
        this.addRenderableWidget(Button.builder(unlimitedLabel(config), button -> {
            config.unlimitedCycle = !config.unlimitedCycle;
            config.save();
            button.setMessage(unlimitedLabel(config));
        }).bounds(centerX - 110, y, 220, 20).build());

        y += 25;
        this.addRenderableWidget(Button.builder(storageLabel(config), button -> {
            config.storageEnabled = !config.storageEnabled;
            config.save();
            Minecraft.getInstance().setScreen(new AutomationScreen());
        }).bounds(centerX - 110, y, 220, 20).build());

        if (config.storageEnabled) {
            y += 25;
            this.addRenderableWidget(Button.builder(Component.literal("Depolama türlerini seç"),
                    button -> Minecraft.getInstance().setScreen(new StorageTypesScreen(this)))
                    .bounds(centerX - 110, y, 220, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        AutomationState state = manager.getState();
        graphics.drawCenteredString(this.font, Component.translatable("screen.farmerautomation.title"),
                this.width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Durum: " + state.name()),
                this.width / 2, 30, 0xFFFF55);

        int y = 225;
        y = drawInfo(graphics, y, "Çalışıyor: " + (manager.isRunning() ? "Evet" : "Hayır"));
        y = drawInfo(graphics, y, "Bulunan köylü: " + manager.getVillagerCount());
        y = drawInfo(graphics, y, "Aktif köylü: " + activeVillagerText());
        y = drawInfo(graphics, y, "Toplam zümrüt: " + manager.getTotalEmeralds());
        y = drawInfo(graphics, y, "Envanterdeki havuç: " + inventoryCarrotText());
        y = drawInfo(graphics, y, "Shulker doluluk: " + shulkerText());
        y = drawInfo(graphics, y, "Son bilgi: " + ErrorHandler.getLastInfo());
        drawInfo(graphics, y + 5, "Son hata: " + ErrorHandler.getLastError());
    }

    private static Component unlimitedLabel(AutomationConfig config) {
        return Component.literal("Sınırsız döngü: " + (config.unlimitedCycle ? "Açık" : "Kapalı"));
    }

    private static Component storageLabel(AutomationConfig config) {
        return Component.literal("Depolamayı aç: " + (config.storageEnabled ? "Açık" : "Kapalı"));
    }

    private int drawInfo(GuiGraphics graphics, int y, String text) {
        graphics.drawString(this.font, text, this.width / 2 - 120, y, 0xFFFFFF, true);
        return y + 14;
    }

    private String activeVillagerText() {
        int index = manager.getCurrentVillagerIndex();
        return index < 0 ? "Yok" : (index + 1) + ". köylü";
    }

    private String inventoryCarrotText() {
        return manager.getCarrotsInInventory() < 0 ? "Henüz okunmuyor" : String.valueOf(manager.getCarrotsInInventory());
    }

    private String shulkerText() {
        return manager.getShulkerTotalSlots() <= 0 ? "Henüz okunmuyor"
                : manager.getShulkerUsedSlots() + "/" + manager.getShulkerTotalSlots();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
