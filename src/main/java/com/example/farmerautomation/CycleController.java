package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Aşama B: en yakın erişilebilir çiftçiyle GERÇEKTEN yalnızca tek havuç -> zümrüt takası.
 * Shift-tıklama kullanılmaz; sonuç slotundan tek tıklamayla alınıp envantere yerleştirilir.
 */
public final class CycleController {
    private static final int SCREEN_TIMEOUT = 60;
    private static final int OFFERS_TIMEOUT = 40;
    private static final int RESULT_TIMEOUT = 20;
    private static final int MAX_SELECT_RETRIES = 2;
    private static final int CLICK_TIMEOUT = 20;
    private static final int VERIFY_TIMEOUT = 40;
    private static final int CLOSE_TIMEOUT = 40;
    private static final int CLOSE_WAIT = 5;

    private static final int PAYMENT_SLOT_A = 0;
    private static final int RESULT_SLOT = 2;

    private enum Step {
        START, SCAN, INTERACT, WAIT_SCREEN, WAIT_OFFERS, SELECT_TRADE, WAIT_RESULT,
        TAKE_RESULT, WAIT_TAKE, PLACE_RESULT, WAIT_PLACE, VERIFY_TRADE,
        CLOSE, WAIT_CLOSE, FINISH, DONE
    }

    private Step step = Step.START;
    private int ticks;
    private int selectRetries;

    private final List<Villager> farmers = new ArrayList<>();
    private Villager current;
    private int offerIndex;
    private int cost;
    private int resultCount;
    private int carrotsBefore;
    private int emeraldsBefore;
    private int targetSlot;
    private int tradesDone;
    private String finishMessage;

    public void reset() {
        step = Step.START;
        ticks = 0;
        selectRetries = 0;
        farmers.clear();
        current = null;
        offerIndex = -1;
        cost = 0;
        resultCount = 0;
        carrotsBefore = 0;
        emeraldsBefore = 0;
        targetSlot = -1;
        tradesDone = 0;
        finishMessage = null;
    }

    /** true: iş bitti (başarılı ya da fail çağrıldı). Çağıran durumu kontrol eder. */
    public boolean tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return step == Step.DONE;
        }

        if (step == Step.DONE) return true;

        try {
            switch (step) {
                case START -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        return fail(client, "Başlangıçta açık bir konteyner menüsü var.");
                    }
                    if (InventoryManager.countCarrots(client) <= 0) {
                        return fail(client, "Envanterde havuç yok.");
                    }
                    step = Step.SCAN;
                }
                case SCAN -> {
                    farmers.clear();
                    farmers.addAll(FarmerScanner.scanAccessible(client));
                    AutomationManager.INSTANCE.setVillagerCount(farmers.size());
                    if (farmers.isEmpty()) {
                        return fail(client, "Erişilebilir çiftçi bulunamadı (menzil/görüş).");
                    }
                    current = farmers.get(0);
                    AutomationManager.INSTANCE.setCurrentVillagerIndex(0);
                    chat(client, farmers.size() + " erişilebilir çiftçi; en yakınla tek takas denenecek.");
                    step = Step.INTERACT;
                }
                case INTERACT -> {
                    if (!FarmerScanner.isAccessible(client, current)) {
                        return fail(client, "Çiftçi etkileşimden önce erişilemez oldu.");
                    }
                    client.gameMode.interact(client.player, current, InteractionHand.MAIN_HAND);
                    ticks = 0;
                    step = Step.WAIT_SCREEN;
                }
                case WAIT_SCREEN -> {
                    if (client.screen instanceof MerchantScreen) {
                        ticks = 0;
                        step = Step.WAIT_OFFERS;
                    } else if (++ticks > SCREEN_TIMEOUT) {
                        return fail(client, "Takas menüsü zamanında açılmadı.");
                    }
                }
                case WAIT_OFFERS -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü teklifler gelmeden kapandı.");
                    }
                    MerchantMenu menu = screen.getMenu();
                    if (menu.getOffers().isEmpty()) {
                        if (++ticks > OFFERS_TIMEOUT) return fail(client, "Takas listesi zamanında gelmedi.");
                        return false;
                    }
                    if (!locateOffer(client, menu)) return true;
                    step = Step.SELECT_TRADE;
                }
                case SELECT_TRADE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü beklenmedik şekilde kapandı.");
                    }
                    int carrots = InventoryManager.countCarrots(client);
                    if (carrots < cost) {
                        return fail(client, "Havuç yetmiyor: " + carrots + " var, takas " + cost + " istiyor.");
                    }
                    carrotsBefore = carrots;
                    emeraldsBefore = InventoryManager.countEmeralds(client);
                    sendSelect(client, screen.getMenu());
                    ticks = 0;
                    step = Step.WAIT_RESULT;
                }
                case WAIT_RESULT -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü sonuç gelmeden kapandı.");
                    }
                    MerchantMenu menu = screen.getMenu();
                    ItemStack result = menu.getSlot(RESULT_SLOT).getItem();
                    ticks++;
                    // Sunucu yanıtı gelsin diye en az 4 tick bekle, sonra sonucu al.
                    if (ticks >= 4 && !result.isEmpty() && result.is(Items.EMERALD) && result.getCount() == resultCount) {
                        step = Step.TAKE_RESULT;
                        return false;
                    }
                    if (ticks > RESULT_TIMEOUT) {
                        if (++selectRetries > MAX_SELECT_RETRIES) {
                            return fail(client, diagnoseNoResult(client, menu));
                        }
                        sendSelect(client, menu);
                        ticks = 0;
                    }
                }
                case TAKE_RESULT -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü beklenmedik şekilde kapandı.");
                    }
                    MerchantMenu menu = screen.getMenu();
                    targetSlot = findPlaceSlot(menu);
                    if (targetSlot < 0) {
                        return fail(client, "Envanterde zümrüt için yer yok; takas sonucu alınmadı.");
                    }
                    // Tek normal tıklama: sonucu imlece al (Shift-tıklama yok).
                    client.gameMode.handleInventoryMouseClick(menu.containerId, RESULT_SLOT, 0, ClickType.PICKUP, client.player);
                    ticks = 0;
                    step = Step.WAIT_TAKE;
                }
                case WAIT_TAKE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü sonuç alınırken kapandı.");
                    }
                    ItemStack carried = screen.getMenu().getCarried();
                    if (!carried.isEmpty() && carried.is(Items.EMERALD)) {
                        step = Step.PLACE_RESULT;
                    } else if (++ticks > CLICK_TIMEOUT) {
                        return fail(client, "Sonuç slotundan zümrüt alınamadı.");
                    }
                }
                case PLACE_RESULT -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü zümrüt yerleştirilirken kapandı.");
                    }
                    MerchantMenu menu = screen.getMenu();
                    client.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, client.player);
                    ticks = 0;
                    step = Step.WAIT_PLACE;
                }
                case WAIT_PLACE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü zümrüt yerleştirilirken kapandı.");
                    }
                    if (screen.getMenu().getCarried().isEmpty()) {
                        ticks = 0;
                        step = Step.VERIFY_TRADE;
                    } else if (++ticks > CLICK_TIMEOUT) {
                        return fail(client, "Zümrüt envantere yerleştirilemedi (imlecte kaldı).");
                    }
                }
                case VERIFY_TRADE -> {
                    int carrots = InventoryManager.countCarrots(client);
                    int emeralds = InventoryManager.countEmeralds(client);
                    boolean carrotsOk = carrots == carrotsBefore - cost;
                    boolean emeraldsOk = emeralds == emeraldsBefore + resultCount;
                    if (carrotsOk && emeraldsOk) {
                        tradesDone++;
                        chat(client, "Takas doğrulandı: havuç " + carrotsBefore + " -> " + carrots
                                + ", zümrüt " + emeraldsBefore + " -> " + emeralds + ".");
                        AutomationManager.INSTANCE.addEmeralds(resultCount);
                        finishMessage = "Tek takas tamamlandı (Aşama B). Havuç -" + cost + ", zümrüt +" + resultCount + ".";
                        step = Step.CLOSE;
                    } else if (++ticks > VERIFY_TIMEOUT) {
                        return fail(client, "Takas doğrulanamadı: havuç " + carrotsBefore + " -> " + carrots
                                + " (beklenen " + (carrotsBefore - cost) + "), zümrüt " + emeraldsBefore + " -> "
                                + emeralds + " (beklenen " + (emeraldsBefore + resultCount) + ").");
                    }
                }
                case CLOSE -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        client.player.closeContainer();
                    }
                    ticks = 0;
                    step = Step.WAIT_CLOSE;
                }
                case WAIT_CLOSE -> {
                    ticks++;
                    if (!(client.screen instanceof AbstractContainerScreen<?>)) {
                        if (ticks >= CLOSE_WAIT) step = Step.FINISH;
                    } else {
                        if (ticks % 10 == 0) client.player.closeContainer();
                        if (ticks > CLOSE_TIMEOUT) return fail(client, "Takas menüsü kapatılamadı.");
                    }
                }
                case FINISH -> {
                    chat(client, finishMessage == null ? "Döngü bitti." : finishMessage);
                    step = Step.DONE;
                    return true;
                }
                case DONE -> {
                    return true;
                }
            }
        } catch (Exception exception) {
            return fail(client, "Döngü hatası: " + exception.getClass().getSimpleName());
        }

        return step == Step.DONE;
    }

    /** Teklif listesinde havuç -> zümrüt takasını bulur. Bulunamazsa fail eder ve false döner. */
    private boolean locateOffer(Minecraft client, MerchantMenu menu) {
        int index = 0;
        for (MerchantOffer offer : menu.getOffers()) {
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult();

            if (costA.is(Items.CARROT) && costB.isEmpty() && result.is(Items.EMERALD)) {
                if (offer.isOutOfStock()) {
                    fail(client, "Havuç -> zümrüt takasının stoğu bitmiş.");
                    return false;
                }
                offerIndex = index;
                cost = costA.getCount();
                resultCount = result.getCount();
                chat(client, "Takas bulundu: " + cost + " havuç -> " + resultCount + " zümrüt (teklif " + (index + 1) + ").");
                return true;
            }
            index++;
        }

        fail(client, "Bu çiftçide havuç -> zümrüt takası yok.");
        return false;
    }

    private void sendSelect(Minecraft client, MerchantMenu menu) {
        menu.setSelectionHint(offerIndex);
        menu.tryMoveItems(offerIndex);
        client.player.connection.send(new ServerboundSelectTradePacket(offerIndex));
    }

    private String diagnoseNoResult(Minecraft client, MerchantMenu menu) {
        ItemStack payment = menu.getSlot(PAYMENT_SLOT_A).getItem();
        int carrots = InventoryManager.countCarrots(client);

        if (payment.is(Items.CARROT) && payment.getCount() >= cost) {
            return "Havuçlar takas slotuna yerleşti ama sunucu sonuç vermedi; stok bitmiş ya da takas kilitli olabilir.";
        }
        if (carrots < cost) {
            return "Havuç yetmiyor: " + carrots + " var, takas " + cost + " istiyor.";
        }
        return "Sunucu takas seçimine yanıt vermedi (havuç takas slotuna taşınmadı).";
    }

    /** Menüdeki oyuncu envanteri slotları arasında zümrüt için boş ya da yer olan slotu bulur. */
    private int findPlaceSlot(MerchantMenu menu) {
        int start = Math.max(0, menu.slots.size() - 36);
        int firstEmpty = -1;

        for (int i = start; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();

            if (stack.isEmpty()) {
                if (firstEmpty < 0) firstEmpty = i;
            } else if (stack.is(Items.EMERALD) && stack.getCount() + resultCount <= stack.getMaxStackSize()) {
                return i;
            }
        }

        return firstEmpty;
    }

    private boolean fail(Minecraft client, String message) {
        if (client != null && client.player != null && client.screen instanceof AbstractContainerScreen<?>) {
            client.player.closeContainer();
        }
        chat(client, "HATA: " + message + " Güvenli şekilde durduruldu.");
        step = Step.DONE;
        AutomationManager.INSTANCE.fail("Döngü: " + message);
        return true;
    }

    private static void chat(Minecraft client, String message) {
        ErrorHandler.info(message);
        if (client != null && client.player != null) {
            client.player.displayClientMessage(Component.literal("§6[Döngü]§r " + message), false);
        }
    }
}
