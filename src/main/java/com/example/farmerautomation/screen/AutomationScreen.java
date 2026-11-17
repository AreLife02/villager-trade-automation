package com.example.farmerautomation.screen;

import com.example.farmerautomation.AutomationManager;
import com.example.farmerautomation.AutomationState;
import com.example.farmerautomation.ErrorHandler;
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
        int y = 45;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.farmerautomation.start"),
                        button -> manager.start())
                .bounds(centerX - 105, y, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.farmerautomation.cycle"),
                        button -> manager.startCycle())
                .bounds(centerX + 5, y, 100, 20)
                .build());

        y += 25;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.farmerautomation.pause"),
                        button -> manager.pause())
                .bounds(centerX - 105, y, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.farmerautomation.resume"),
                        button -> manager.resume())
                .bounds(centerX + 5, y, 100, 20)
                .build());

        y += 25;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.farmerautomation.diagnostic"),
                        button -> manager.startDiagnostic())
                .bounds(centerX - 105, y, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.farmerautomation.stop"),
                        button -> manager.stop())
                .bounds(centerX + 5, y, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        AutomationState state = manager.getState();
        graphics.drawCenteredString(this.font, Component.translatable("screen.farmerautomation.title"),
                this.width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Durum: " + state.name()),
                this.width / 2, 30, 0xFFFF55);

        int y = 135;
        y = drawInfo(graphics, y, "Çalışıyor: " + yesNo(manager.isRunning()));
        y = drawInfo(graphics, y, "Bulunan köylü: " + manager.getVillagerCount());
        y = drawInfo(graphics, y, "Aktif köylü: " + activeVillagerText());
        y = drawInfo(graphics, y, "Toplam zümrüt: " + manager.getTotalEmeralds());
        y = drawInfo(graphics, y, "Envanterdeki havuç: " + inventoryCarrotText());
        y = drawInfo(graphics, y, "Shulker doluluk: " + shulkerText());
        y = drawInfo(graphics, y, "Son bilgi: " + ErrorHandler.getLastInfo());
        drawInfo(graphics, y + 5, "Son hata: " + ErrorHandler.getLastError());
    }

    private int drawInfo(GuiGraphics graphics, int y, String text) {
        graphics.drawString(this.font, text, this.width / 2 - 120, y, 0xFFFFFF, true);
        return y + 14;
    }

    private String yesNo(boolean value) {
        return value ? "Evet" : "Hayır";
    }

    private String activeVillagerText() {
        int index = manager.getCurrentVillagerIndex();
        return index < 0 ? "Yok" : (index + 1) + ". köylü";
    }

    private String inventoryCarrotText() {
        return manager.getCarrotsInInventory() < 0
                ? "Henüz okunmuyor"
                : String.valueOf(manager.getCarrotsInInventory());
    }

    private String shulkerText() {
        return manager.getShulkerTotalSlots() <= 0
                ? "Henüz okunmuyor"
                : manager.getShulkerUsedSlots() + "/" + manager.getShulkerTotalSlots();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
