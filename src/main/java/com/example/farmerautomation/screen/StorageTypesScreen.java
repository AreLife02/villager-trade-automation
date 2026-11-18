package com.example.farmerautomation.screen;

import com.example.farmerautomation.AutomationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StorageTypesScreen extends Screen {
    private final Screen parent;

    private static final String[][] TYPES = {
            {"chest", "Sandık"}, {"trapped_chest", "Tuzaklı Sandık"}, {"barrel", "Varil"},
            {"ender_chest", "Ender Sandığı"}, {"shulker_box", "Standart Shulker"},
            {"white_shulker_box", "Beyaz Shulker"}, {"orange_shulker_box", "Turuncu Shulker"},
            {"magenta_shulker_box", "Eflatun Shulker"}, {"light_blue_shulker_box", "Açık Mavi Shulker"},
            {"yellow_shulker_box", "Sarı Shulker"}, {"lime_shulker_box", "Açık Yeşil Shulker"},
            {"pink_shulker_box", "Pembe Shulker"}, {"gray_shulker_box", "Gri Shulker"},
            {"light_gray_shulker_box", "Açık Gri Shulker"}, {"cyan_shulker_box", "Camgöbeği Shulker"},
            {"purple_shulker_box", "Mor Shulker"}, {"blue_shulker_box", "Mavi Shulker"},
            {"brown_shulker_box", "Kahverengi Shulker"}, {"green_shulker_box", "Yeşil Shulker"},
            {"red_shulker_box", "Kırmızı Shulker"}, {"black_shulker_box", "Siyah Shulker"}
    };

    public StorageTypesScreen(Screen parent) {
        super(Component.literal("Depolama Türleri"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columns = 3;
        int buttonWidth = 104;
        int gap = 4;
        int totalWidth = columns * buttonWidth + (columns - 1) * gap;
        int left = (width - totalWidth) / 2;
        int top = 48;

        for (int i = 0; i < TYPES.length; i++) {
            final String key = TYPES[i][0];
            final String label = TYPES[i][1];
            int x = left + (i % columns) * (buttonWidth + gap);
            int y = top + (i / columns) * 23;
            addRenderableWidget(Button.builder(optionLabel(key, label), b -> {
                AutomationConfig.get().toggleStorageType(key);
                b.setMessage(optionLabel(key, label));
            }).bounds(x, y, buttonWidth, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Geri"),
                b -> Minecraft.getInstance().setScreen(parent))
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private static Component optionLabel(String key, String label) {
        return Component.literal((AutomationConfig.get().isStorageTypeSelected(key) ? "[✓] " : "[ ] ") + label);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Seçilen kutulara yalnızca zümrüt aktarılır"),
                width / 2, 34, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
