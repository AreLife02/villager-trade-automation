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
        super(Component.literal("Köylü Takası"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int left = center - 112;
        int right = center + 4;
        int buttonWidth = 108;
        int y = 52;

        addRenderableWidget(Button.builder(Component.literal("BAŞLAT"),
                button -> manager.start()).bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DURDUR"),
                button -> manager.stop()).bounds(right, y, buttonWidth, 20).build());

        y += 25;
        addRenderableWidget(Button.builder(Component.literal("DURAKLAT"),
                button -> manager.pause()).bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DEVAM ET"),
                button -> manager.resume()).bounds(right, y, buttonWidth, 20).build());

        y += 25;
        addRenderableWidget(Button.builder(Component.literal("KÖYLÜLER"),
                button -> Minecraft.getInstance().setScreen(new VillagerManagementScreen(this)))
                .bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("AYARLAR"),
                button -> Minecraft.getInstance().setScreen(new AutomationSettingsScreen(this)))
                .bounds(right, y, buttonWidth, 20).build());

        AutomationConfig config = AutomationConfig.get();
        y += 25;
        addRenderableWidget(Button.builder(unlimitedLabel(config), button -> {
            config.unlimitedCycle = !config.unlimitedCycle;
            config.save();
            button.setMessage(unlimitedLabel(config));
        }).bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(storageLabel(config), button -> {
            config.storageEnabled = !config.storageEnabled;
            config.save();
            Minecraft.getInstance().setScreen(new AutomationScreen());
        }).bounds(right, y, buttonWidth, 20).build());

        if (config.storageEnabled) {
            y += 25;
            addRenderableWidget(Button.builder(Component.literal("DEPOLAMA TÜRLERİ"),
                    button -> Minecraft.getInstance().setScreen(new StorageTypesScreen(this)))
                    .bounds(center - 112, y, 224, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int center = this.width / 2;
        int panelLeft = center - 132;
        int panelRight = center + 132;
        int panelTop = 8;
        int panelBottom = Math.min(this.height - 6, 238);

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE9121720);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFF39C76A);
        graphics.fill(panelLeft, 43, panelRight, 44, 0xFF35404D);

        AutomationState state = manager.getState();
        graphics.drawCenteredString(this.font, Component.literal("KÖYLÜ TAKAS OTOMASYONU"),
                center, 15, 0xFFF4F7FA);
        graphics.drawCenteredString(this.font, Component.literal(stateLabel(state)),
                center, 29, manager.isRunning() ? 0xFF58E58B : 0xFFB9C4D0);

        super.render(graphics, mouseX, mouseY, partialTick);

        int statsTop = 177;
        int statsBottom = Math.min(this.height - 10, 231);
        graphics.fill(panelLeft + 10, statsTop, panelRight - 10, statsBottom, 0xFF202A35);
        graphics.fill(panelLeft + 10, statsTop, panelRight - 10, statsTop + 1, 0xFF465362);

        int leftX = panelLeft + 20;
        int rightX = center + 8;
        int row1 = statsTop + 9;
        int row2 = row1 + 15;
        int row3 = row2 + 15;

        drawMetric(graphics, leftX, row1, "Köylüler", String.valueOf(manager.getVillagerCount()));
        drawMetric(graphics, rightX, row1, "Aktif hedef", activeVillagerText());
        drawMetric(graphics, leftX, row2, "Toplam zümrüt", String.valueOf(manager.getTotalEmeralds()));
        drawMetric(graphics, rightX, row2, "Takas stoğu", tradeStockText());
        drawMetric(graphics, leftX, row3, "Havuç", inventoryCarrotText());
        drawMetric(graphics, rightX, row3, "Depolama", shulkerText());

        String error = ErrorHandler.getLastError();
        if (error != null && !error.isBlank()) {
            graphics.drawCenteredString(this.font, Component.literal(trim(error, 48)),
                    center, Math.min(this.height - 5, 242), 0xFFFF7777);
        }
    }

    private void drawMetric(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(this.font, label + ": ", x, y, 0xFF9EABB9, false);
        int labelWidth = this.font.width(label + ": ");
        graphics.drawString(this.font, trim(value, 16), x + labelWidth, y, 0xFFF1F5F9, false);
    }

    private static String stateLabel(AutomationState state) {
        return switch (state) {
            case IDLE -> "Hazır";
            case CYCLE -> "Takas yapılıyor";
            case PAUSED -> "Duraklatıldı";
            case COMPLETED -> "Tamamlandı";
            case ERROR -> "Hata";
            case OPENING_SHOP -> "Market açılıyor";
            case BUYING_CARROTS -> "Havuç alınıyor";
            case DIAGNOSTIC -> "Kontrol ediliyor";
            case SCANNING_VILLAGERS -> "Köylüler taranıyor";
            case SELECTING_VILLAGER -> "Köylü seçiliyor";
            case NAVIGATING -> "Köylüye gidiliyor";
            case TRADING -> "Takas yapılıyor";
            case CHECKING_STOCK -> "Stok kontrol ediliyor";
            case COLLECTING_EMERALDS -> "Zümrütler alınıyor";
            case STORING_ITEMS -> "Eşyalar depolanıyor";
            case SWITCHING_VILLAGER -> "Sonraki köylü";
        };
    }

    private static Component unlimitedLabel(AutomationConfig config) {
        return Component.literal("Sınırsız: " + (config.unlimitedCycle ? "Açık" : "Kapalı"));
    }

    private static Component storageLabel(AutomationConfig config) {
        return Component.literal("Depolama: " + (config.storageEnabled ? "Açık" : "Kapalı"));
    }

    private String activeVillagerText() {
        int index = manager.getCurrentVillagerIndex();
        return index < 0 ? "-" : (index + 1) + "/" + manager.getVillagerCount();
    }

    private String tradeStockText() {
        int remaining = manager.getCurrentTradeStockRemaining();
        return remaining < 0 ? "-" : String.valueOf(remaining);
    }

    private String inventoryCarrotText() {
        int carrots = manager.getCarrotsInInventory();
        return carrots < 0 ? "-" : String.valueOf(carrots);
    }

    private String shulkerText() {
        return manager.getShulkerTotalSlots() <= 0 ? "-"
                : manager.getShulkerUsedSlots() + "/" + manager.getShulkerTotalSlots();
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
