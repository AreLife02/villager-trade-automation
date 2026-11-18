package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

/** Phase B: en yakın çiftçiye güvenli Baritone rotası ve isteğe bağlı takas ekranı. */
public final class FarmerNavigationController {
    private enum Step { SCAN, NAVIGATE, WAIT_TRADE_SCREEN, DONE }

    private final BaritoneNavigation baritone = new BaritoneNavigation();
    private Step step = Step.SCAN;
    private Villager target;
    private boolean openTradeScreen;
    private int elapsedTicks;
    private int idleTicks;

    public void reset(boolean openTradeScreen) {
        baritone.stop();
        this.openTradeScreen = openTradeScreen;
        step = Step.SCAN;
        target = null;
        elapsedTicks = 0;
        idleTicks = 0;
    }

    /** true dönerse işlem tamamlanmış veya güvenli şekilde durdurulmuştur. */
    public boolean tick(Minecraft client) {
        if (step == Step.DONE) return true;
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return fail("Oyuncu veya oyun dünyası hazır değil.");
        }

        try {
            switch (step) {
                case SCAN -> {
                    List<Villager> farmers = FarmerScanner.scanFarmers(client);
                    AutomationManager.INSTANCE.setVillagerCount(farmers.size());
                    if (farmers.isEmpty()) return fail("Tarama alanında canlı çiftçi bulunamadı.");

                    target = farmers.getFirst();
                    AutomationManager.INSTANCE.setCurrentVillagerIndex(0);
                    BlockPos targetPos = target.blockPosition();

                    if (!baritone.start(targetPos)) {
                        return fail("Baritone başlatılamadı. Baritone'un 1.21.11 Fabric sürümünün kurulu olduğundan emin ol. Ayrıntı: "
                                + baritone.getLastError());
                    }

                    elapsedTicks = 0;
                    idleTicks = 0;
                    step = Step.NAVIGATE;
                    AutomationManager.INSTANCE.setState(AutomationState.NAVIGATING);
                    info("En yakın çiftçi seçildi: X=" + targetPos.getX()
                            + " Y=" + targetPos.getY() + " Z=" + targetPos.getZ()
                            + ". Blok kırma ve yerleştirme kapalı.");
                }
                case NAVIGATE -> {
                    if (target == null || !target.isAlive()) {
                        return fail("Seçilen çiftçi artık mevcut değil.");
                    }

                    if (FarmerScanner.isAccessible(client, target)) {
                        baritone.finish();
                        info("Çiftçinin yanına ulaşıldı; etkileşim mesafesi ve görüş doğrulandı.");
                        if (!openTradeScreen) {
                            step = Step.DONE;
                            return true;
                        }

                        client.gameMode.interact(client.player, target, InteractionHand.MAIN_HAND);
                        elapsedTicks = 0;
                        step = Step.WAIT_TRADE_SCREEN;
                        AutomationManager.INSTANCE.setState(AutomationState.TRADING);
                        info("Takas ekranı bekleniyor. Otomatik takas yapılmayacak.");
                        return false;
                    }

                    elapsedTicks++;
                    if (elapsedTicks > Math.max(40, AutomationConfig.get().navigationTimeoutTicks)) {
                        return fail("Köylüye ulaşmak için ayrılan süre doldu.");
                    }

                    BaritoneNavigation.PathStatus status = baritone.getPathStatus();
                    if (status == BaritoneNavigation.PathStatus.ACTIVE) {
                        idleTicks = 0;
                    } else if (++idleTicks > 40) {
                        return fail("Baritone geçerli bir yol bulamadı veya yol hedefe ulaşmadan sona erdi.");
                    }
                }
                case WAIT_TRADE_SCREEN -> {
                    if (client.screen instanceof MerchantScreen) {
                        info("Takas ekranı açıldı. Ekran açık bırakıldı; hiçbir takas yapılmadı.");
                        step = Step.DONE;
                        return true;
                    }
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        return fail("Köylü takas ekranı yerine farklı bir menü açıldı.");
                    }
                    if (++elapsedTicks > 100) {
                        return fail("Köylü takas ekranı zamanında açılmadı.");
                    }
                }
                case DONE -> { return true; }
            }
        } catch (Exception exception) {
            return fail("Navigasyon hatası: " + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " - " + exception.getMessage()));
        }
        return false;
    }

    public void pause() {
        if (step == Step.NAVIGATE) {
            baritone.stop();
            info("Navigasyon duraklatıldı.");
        }
    }

    public boolean resume() {
        if (step != Step.NAVIGATE || target == null) return true;
        if (!target.isAlive()) {
            return fail("Duraklatma sırasında seçilen çiftçi artık mevcut değil.");
        }
        if (!baritone.start(target.blockPosition())) {
            return fail("Navigasyon devam ettirilemedi. Ayrıntı: " + baritone.getLastError());
        }
        idleTicks = 0;
        info("Köylüye giden yol yeniden başlatıldı.");
        return true;
    }

    public void stop() {
        baritone.stop();
        target = null;
        step = Step.DONE;
    }

    private boolean fail(String message) {
        baritone.stop();
        step = Step.DONE;
        ErrorHandler.error(message);
        AutomationManager.INSTANCE.fail(message);
        return true;
    }

    private static void info(String message) {
        ErrorHandler.info(message);
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6[Otomasyon]§r " + message), false);
        }
    }
}
