package com.example.farmerautomation.screen;

import com.example.farmerautomation.AutomationConfig;
import com.example.farmerautomation.AutomationManager;
import com.example.farmerautomation.trade.TradeProfession;
import com.example.farmerautomation.trade.VillagerScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public final class VillagerManagementScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private final Screen parent;
    private final List<Villager> villagers = new ArrayList<>();
    private int page;

    public VillagerManagementScreen(Screen parent) {
        super(Component.literal("Köylü Yönetimi"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        scanVillagers();
        rebuildWidgets();
    }

    private void scanVillagers() {
        villagers.clear();
        Minecraft client = Minecraft.getInstance();
        villagers.addAll(VillagerScanner.scan(client, EnumSet.allOf(TradeProfession.class)));
        AutomationConfig config = AutomationConfig.get();
        if (config.selectedVillagerIds == null) config.selectedVillagerIds = new ArrayList<>();
        if (!config.villagerSelectionEnabled) {
            config.selectedVillagerIds.clear();
            for (Villager villager : villagers) config.selectedVillagerIds.add(villager.getUUID().toString());
            config.villagerSelectionEnabled = true;
            config.save();
        }
        villagers.sort(Comparator.comparingInt(v -> {
            int index = config.selectedVillagerIds.indexOf(v.getUUID().toString());
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        page = Math.min(page, Math.max(0, (villagers.size() - 1) / PAGE_SIZE));
    }

    private void rebuildWidgets() {
        clearWidgets();
        int center = width / 2;
        int rowY = Math.max(78, height / 2 - 65);
        AutomationConfig config = AutomationConfig.get();

        addRenderableWidget(Button.builder(Component.literal(
                Boolean.FALSE.equals(config.villagerOverlayEnabled) ? "Kutular: Kapalı" : "Kutular: Açık"), b -> {
            config.villagerOverlayEnabled = !Boolean.FALSE.equals(config.villagerOverlayEnabled);
            config.save();
            rebuildWidgets();
        }).bounds(width - 108, 12, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Yeniden Tara"), b -> {
            scanVillagers();
            rebuildWidgets();
        }).bounds(center - 155, 48, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Hepsini Seç"), b -> {
            config.selectedVillagerIds.clear();
            for (Villager villager : villagers) config.selectedVillagerIds.add(villager.getUUID().toString());
            config.save();
            rebuildWidgets();
        }).bounds(center - 50, 48, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Hiçbirini Seçme"), b -> {
            config.selectedVillagerIds.clear();
            config.save();
            rebuildWidgets();
        }).bounds(center + 55, 48, 100, 20).build());

        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(villagers.size(), start + PAGE_SIZE); i++) {
            Villager villager = villagers.get(i);
            String uuid = villager.getUUID().toString();
            int selectedIndex = config.selectedVillagerIds.indexOf(uuid);
            boolean selected = selectedIndex >= 0;
            int y = rowY + (i - start) * 27;
            TradeProfession profession = VillagerScanner.professionOf(villager);
            String professionName = profession == null ? "Bilinmeyen" : profession.displayName();
            String label = (selected ? "✓ " + (selectedIndex + 1) : "□") + "  "
                    + professionName + "  (" + (int) villager.getX() + ", "
                    + (int) villager.getY() + ", " + (int) villager.getZ() + ")";
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                if (config.selectedVillagerIds.contains(uuid)) config.selectedVillagerIds.remove(uuid);
                else config.selectedVillagerIds.add(uuid);
                config.save();
                rebuildWidgets();
            }).bounds(center - 155, y, 265, 22).build());

            addRenderableWidget(Button.builder(Component.literal("↑"), b -> move(uuid, -1))
                    .bounds(center + 113, y, 20, 22).build());
            addRenderableWidget(Button.builder(Component.literal("↓"), b -> move(uuid, 1))
                    .bounds(center + 136, y, 20, 22).build());
        }

        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(center - 155, height - 53, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            page = Math.min(Math.max(0, (villagers.size() - 1) / PAGE_SIZE), page + 1);
            rebuildWidgets();
        }).bounds(center - 115, height - 53, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Geri"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(center - 50, height - 53, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Başlat"), b -> {
            if (config.selectedVillagerIds.isEmpty()) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§cÖnce en az bir köylü seçmelisin."), false);
                }
                return;
            }
            Minecraft.getInstance().setScreen(null);
            AutomationManager.INSTANCE.startCycle();
        }).bounds(center + 55, height - 53, 100, 20).build());
    }

    private void move(String uuid, int direction) {
        AutomationConfig config = AutomationConfig.get();
        int index = config.selectedVillagerIds.indexOf(uuid);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= config.selectedVillagerIds.size()) return;
        String other = config.selectedVillagerIds.get(target);
        config.selectedVillagerIds.set(target, uuid);
        config.selectedVillagerIds.set(index, other);
        config.save();
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        AutomationConfig config = AutomationConfig.get();
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(
                "Bulunan: " + villagers.size() + "   Seçilen: " + config.selectedVillagerIds.size()
                        + "   Sıra: ↑ / ↓"), width / 2, 34, 0xBBBBBB);
        if (villagers.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("Yakında takas yapabilecek köylü bulunamadı."),
                    width / 2, height / 2, 0xFF7777);
        }
        int pages = Math.max(1, (villagers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawCenteredString(font, Component.literal("Sayfa " + (page + 1) + " / " + pages),
                width / 2, height - 70, 0xAAAAAA);
        graphics.drawCenteredString(font, Component.literal(
                "Seçim ve sıra otomatik kaydedilir. ↑ / ↓ ile sırayı değiştir."),
                width / 2, height - 25, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
