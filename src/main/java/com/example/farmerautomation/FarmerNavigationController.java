package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/** En yakın çiftçiye bağımsız yol bulma ile ulaşır ve gerçek takas ekranını doğrular. */
public final class FarmerNavigationController {
    private enum Step { SCAN, NAVIGATE, WAIT_AIM, WAIT_TRADE_SCREEN, DONE }

    private final LocalNavigation navigation = new LocalNavigation();
    private Step step = Step.SCAN;
    private Villager target;
    private boolean openTradeScreen;
    private int elapsedTicks;
    private int aimAttempts;
    private String lastInteraction = "-";

    public void reset(boolean openTradeScreen) {
        navigation.stop();
        this.openTradeScreen = openTradeScreen;
        step = Step.SCAN;
        target = null;
        elapsedTicks = 0;
        aimAttempts = 0;
        lastInteraction = "-";
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
                    if (FarmerScanner.isAccessible(client, target)) {
                        LocalNavigation.faceEntity(client, target);
                        return interactOrFinish(client);
                    }

                    if (!navigation.start(client, target)) {
                        return fail("Köylüye güvenli bir yol bulunamadı: " + navigation.getLastError());
                    }
                    elapsedTicks = 0;
                    step = Step.NAVIGATE;
                    AutomationManager.INSTANCE.setState(AutomationState.NAVIGATING);
                    info("En yakın çiftçi seçildi. Bağımsız yol bulma başlatıldı; blok kırma/yerleştirme yapılmayacak.");
                }
                case NAVIGATE -> {
                    if (target == null || !target.isAlive()) return fail("Seçilen çiftçi artık mevcut değil.");

                    LocalNavigation.Status status = navigation.tick(client, target);
                    if (status == LocalNavigation.Status.REACHED) {
                        info("Köylüye ulaşıldı; kamera yüzüne çevrildi.");
                        return interactOrFinish(client);
                    }
                    if (status == LocalNavigation.Status.FAILED) {
                        return fail("Köylüye ulaşılamadı: " + navigation.getLastError());
                    }
                    if (++elapsedTicks > Math.max(40, AutomationConfig.get().navigationTimeoutTicks)) {
                        return fail("Köylüye ulaşmak için ayrılan süre doldu.");
                    }
                }
                case WAIT_AIM -> {
                    if (target == null || !target.isAlive()) return fail("Etkileşimden önce seçilen çiftçi artık mevcut değil.");
                    if (++elapsedTicks < 2) return false;

                    if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() == target) {
                        VillagerInteraction.Attempt attempt = VillagerInteraction.interact(client, target, hit);
                        lastInteraction = attempt.describe();
                        elapsedTicks = 0;
                        step = Step.WAIT_TRADE_SCREEN;
                        AutomationManager.INSTANCE.setState(AutomationState.TRADING);
                        info("Nişangâh çiftçiyi doğruladı; vanilla etkileşim akışı gönderildi: " + lastInteraction);
                        return false;
                    }

                    if (++aimAttempts <= 2) {
                        LocalNavigation.faceEntity(client, target);
                        elapsedTicks = 0;
                        info("Nişangâh henüz çiftçiyi hedeflemiyor; kamera yeniden hizalanıyor (" + aimAttempts + "/2).");
                        return false;
                    }
                    String hit = client.hitResult == null ? "yok" : client.hitResult.getType().name();
                    return fail("Takas etkileşimi gönderilmedi: nişangâh çiftçiyi hedeflemiyor (hedef=" + hit + ").");
                }
                case WAIT_TRADE_SCREEN -> {
                    if (client.screen instanceof MerchantScreen) {
                        info("Takas ekranı gerçekten açıldı. Ekran açık bırakıldı; bu testte takas yapılmayacak.");
                        step = Step.DONE;
                        return true;
                    }
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        return fail("Köylü takas ekranı yerine farklı bir menü açıldı.");
                    }
                    if (++elapsedTicks > 100) return fail("Köylü takas ekranı zamanında açılmadı (etkileşim: " + lastInteraction + ").");
                }
                case DONE -> { return true; }
            }
        } catch (Exception exception) {
            return fail("Navigasyon hatası: " + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " - " + exception.getMessage()));
        }
        return false;
    }

    private boolean interactOrFinish(Minecraft client) {
        if (!openTradeScreen) {
            step = Step.DONE;
            return true;
        }
        if (target == null || !FarmerScanner.isAccessible(client, target)) {
            return fail("Köylüye ulaşıldı ancak etkileşim mesafesi veya görüş doğrulanamadı.");
        }
        LocalNavigation.faceEntity(client, target);
        elapsedTicks = 0;
        aimAttempts = 0;
        step = Step.WAIT_AIM;
        info("Kamera köylüye çevrildi; nişangâh doğrulandıktan sonra vanilla etkileşim akışı gönderilecek.");
        return false;
    }

    public void pause() {
        if (step == Step.NAVIGATE) {
            navigation.stop();
            info("Navigasyon duraklatıldı.");
        }
    }

    public boolean resume(Minecraft client) {
        if (step != Step.NAVIGATE || target == null) return true;
        if (!target.isAlive()) return fail("Duraklatma sırasında seçilen çiftçi artık mevcut değil.");
        if (!navigation.start(client, target)) {
            return fail("Navigasyon devam ettirilemedi: " + navigation.getLastError());
        }
        elapsedTicks = 0;
        info("Köylüye giden rota yeniden hesaplandı.");
        return true;
    }

    public void stop() {
        navigation.stop();
        target = null;
        step = Step.DONE;
    }

    private boolean fail(String message) {
        navigation.stop();
        step = Step.DONE;
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
