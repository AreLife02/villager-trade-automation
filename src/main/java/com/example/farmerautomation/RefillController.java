package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/**
 * Shop'tan havuç tamamlar: envanterde toplam en az 768 havuç olana kadar 64'lük alımlar (yukarı yuvarlar).
 * Alım sayısı körlemesine 12 değildir: her alımdan önce yer kontrol edilir, her alım envanter artışıyla doğrulanır.
 * ShopController'a dokunmaz.
 */
public final class RefillController {
    public enum Result { RUNNING, DONE, FAILED }

    public static final int TARGET_CARROTS = 768;
    private static final int STACK = 64;
    private static final int MAX_PURCHASES = 14;
    private static final int SHOP_TIMEOUT = 100;
    private static final int SETTLE_TICKS = 1;
    private static final int MENU_WAIT = 1;
    private static final int PURCHASE_MIN_WAIT = 1;
    private static final int PURCHASE_TIMEOUT = 20;
    private static final int CLOSE_WAIT = 1;
    private static final int CLOSE_TIMEOUT = 40;

    private enum Step {
        START, WAIT_SHOP, SETTLE, CLICK_CATEGORY, WAIT_CATEGORY, CLICK_CARROT, WAIT_CARROT,
        CLICK_PLUS, WAIT_PLUS, CONFIRM, WAIT_PURCHASE, CLOSE, WAIT_CLOSE, DONE
    }

    private Step step = Step.START;
    private int ticks;
    private int purchases;
    private int carrotsBefore;
    private boolean limitedByCapacity;
    private String pendingError;
    private String error;
    private boolean plusSelected;

    public void reset() {
        step = Step.START;
        ticks = 0;
        purchases = 0;
        carrotsBefore = 0;
        limitedByCapacity = false;
        pendingError = null;
        error = null;
        plusSelected = false;
    }

    public boolean wasLimitedByCapacity() {
        return limitedByCapacity;
    }

    public String getError() {
        return error;
    }

    public Result tick(Minecraft client) {
        if (client == null || client.player == null) return Result.RUNNING;
        if (step == Step.DONE) return error != null ? Result.FAILED : Result.DONE;

        try {
            switch (step) {
                case START -> {
                    int carrots = InventoryManager.countCarrots(client);
                    if (carrots >= TARGET_CARROTS) {
                        chat(client, "Envanterde zaten " + carrots + " havuç var; Shop'a gerek yok.");
                        step = Step.DONE;
                        return Result.DONE;
                    }
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        error = "Shop açılamadı: başka bir menü açık.";
                        step = Step.DONE;
                        return Result.FAILED;
                    }
                    int capacity = InventoryManager.remainingCarrotCapacity(client);
                    if (capacity < STACK) {
                        error = "Envanterde havuç için yer yok (" + capacity + " havuçluk boşluk); Shop alımı yapılmadı.";
                        step = Step.DONE;
                        return Result.FAILED;
                    }
                    int planned = (TARGET_CARROTS - carrots + STACK - 1) / STACK;
                    chat(client, "Shop'a gidiliyor: " + carrots + " havuç var, hedef " + TARGET_CARROTS
                            + " (en fazla " + planned + " alım, 64'lük).");
                    String command = AutomationConfig.get().shopCommand;
                    if (client.player.connection == null) {
                        error = "Sunucu bağlantısı yok; Shop açılamadı.";
                        step = Step.DONE;
                        return Result.FAILED;
                    }
                    client.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
                    ticks = 0;
                    step = Step.WAIT_SHOP;
                }
                case WAIT_SHOP -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        ticks = 0;
                        step = Step.CLICK_CATEGORY;
                    } else if (++ticks > SHOP_TIMEOUT) {
                        error = "Shop menüsü zamanında açılmadı.";
                        step = Step.DONE;
                        return Result.FAILED;
                    }
                }
                case CLICK_CATEGORY -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return lost(client, "Shop menüsü kapandı.");
                    Slot slot = ShopGui.findSlotByItem(screen, Items.GOLDEN_CARROT);
                    if (slot == null) return failAndClose("Shop menüsünde Altın Havuç bulunamadı.");
                    ShopGui.clickSlot(screen, slot);
                    ticks = MENU_WAIT;
                    step = Step.WAIT_CATEGORY;
                }
                case WAIT_CATEGORY -> {
                    if (--ticks <= 0) step = Step.CLICK_CARROT;
                }
                case CLICK_CARROT -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return lost(client, "Shop menüsü kapandı.");
                    Slot slot = ShopGui.findSlotByItem(screen, Items.CARROT);
                    if (slot == null) return failAndClose("Kategori menüsünde havuç bulunamadı.");
                    ShopGui.clickSlot(screen, slot);
                    ticks = MENU_WAIT;
                    step = Step.WAIT_CARROT;
                }
                case WAIT_CARROT -> {
                    if (--ticks <= 0) step = Step.CLICK_PLUS;
                }
                case CLICK_PLUS -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return lost(client, "Shop menüsü kapandı.");
                    Slot slot = ShopGui.findSlotByText(screen, "+10", "10 set arttır", "+160");
                    if (slot == null) return failAndClose("64 havuç seçimi için +10 düğmesi bulunamadı.");
                    ShopGui.clickSlot(screen, slot);
                    plusSelected = true;
                    ticks = MENU_WAIT;
                    step = Step.WAIT_PLUS;
                }
                case WAIT_PLUS -> {
                    if (--ticks <= 0) step = Step.CONFIRM;
                }
                case CONFIRM -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return lost(client, "Shop menüsü kapandı.");
                    if (!plusSelected) return failAndClose("64'lük miktar seçilmedi.");
                    if (purchases >= MAX_PURCHASES) return failAndClose("Alım güvenlik sınırına ulaşıldı (" + MAX_PURCHASES + ").");
                    if (InventoryManager.remainingCarrotCapacity(client) < STACK) {
                        limitedByCapacity = true;
                        chat(client, "Envanterde 64 havuç daha alacak yer yok; daha fazla alınmıyor.");
                        step = Step.CLOSE;
                        return Result.RUNNING;
                    }
                    Slot slot = ShopGui.findSlotByItem(screen, Items.EMERALD_BLOCK);
                    if (slot == null) return failAndClose("Satın alma onayı için zümrüt bloğu bulunamadı.");
                    carrotsBefore = InventoryManager.countCarrots(client);
                    ShopGui.clickSlot(screen, slot);
                    purchases++;
                    ticks = 0;
                    step = Step.WAIT_PURCHASE;
                }
                case WAIT_PURCHASE -> {
                    ticks++;
                    if (ticks < PURCHASE_MIN_WAIT) return Result.RUNNING;
                    int now = InventoryManager.countCarrots(client);
                    if (now <= carrotsBefore) {
                        if (ticks >= PURCHASE_TIMEOUT) {
                            return failAndClose("Satın alma sonrası havuç sayısı artmadı (" + now + ").");
                        }
                        return Result.RUNNING;
                    }
                    AutomationManager.INSTANCE.setCarrotsInInventory(now);
                    if (now >= TARGET_CARROTS) {
                        step = Step.CLOSE;
                    } else if (purchases >= MAX_PURCHASES) {
                        return failAndClose("Alım sınırına ulaşıldı; hedef havuç miktarı tamamlanamadı.");
                    } else {
                        // Start the next purchase immediately when the server confirms this one.
                        Slot nextPurchase = ShopGui.findSlotByItem(screen, Items.EMERALD_BLOCK);
                        if (nextPurchase == null) {
                            step = Step.CONFIRM;
                        } else if (InventoryManager.remainingCarrotCapacity(client) < STACK) {
                            limitedByCapacity = true;
                            step = Step.CLOSE;
                        } else {
                            carrotsBefore = now;
                            ShopGui.clickSlot(screen, nextPurchase);
                            purchases++;
                            ticks = 0;
                        }
                    }
                }
                case CLOSE -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) client.player.closeContainer();
                    ticks = 0;
                    step = Step.WAIT_CLOSE;
                }
                case WAIT_CLOSE -> {
                    ticks++;
                    if (!(client.screen instanceof AbstractContainerScreen<?>)) {
                        if (ticks >= CLOSE_WAIT) {
                            error = pendingError;
                            step = Step.DONE;
                            if (error == null) {
                                chat(client, "Shop alımı bitti: " + InventoryManager.countCarrots(client) + " havuç.");
                            }
                            return error != null ? Result.FAILED : Result.DONE;
                        }
                    } else {
                        if (ticks % 10 == 0) client.player.closeContainer();
                        if (ticks > CLOSE_TIMEOUT) {
                            error = "Shop menüsü kapatılamadı.";
                            step = Step.DONE;
                            return Result.FAILED;
                        }
                    }
                }
                case DONE -> {
                    return error != null ? Result.FAILED : Result.DONE;
                }
            }
        } catch (Exception exception) {
            return failAndClose("Shop hatası: " + exception.getClass().getSimpleName());
        }

        return Result.RUNNING;
    }

    private Result lost(Minecraft client, String message) {
        error = message;
        step = Step.DONE;
        return Result.FAILED;
    }

    private Result failAndClose(String message) {
        pendingError = message;
        step = Step.CLOSE;
        return Result.RUNNING;
    }

    private static void chat(Minecraft client, String message) {
        ErrorHandler.info(message);
    }
}
