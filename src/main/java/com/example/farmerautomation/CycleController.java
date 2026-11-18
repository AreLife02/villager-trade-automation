package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Havuç -> zümrüt takas döngüsü.
 * Yol bulma LocalNavigation ile yapılır; takas adımları ve envanter doğrulaması korunur.
 */
public final class CycleController {
    private static final int SCREEN_TIMEOUT = 60;
    private static final int OFFERS_TIMEOUT = 40;
    private static final int RESULT_TIMEOUT = 10;
    private static final int MAX_SELECT_RETRIES = 2;
    private static final int CLICK_TIMEOUT = 20;
    private static final int VERIFY_TIMEOUT = 40;
    private static final int CLOSE_TIMEOUT = 40;
    private static final int CLOSE_WAIT = 5;
    private static final int MAX_TRADES_PER_VILLAGER = 64;
    private static final int MAX_REFILLS = 20;

    private static final int PAYMENT_SLOT_A = 0;
    private static final int RESULT_SLOT = 2;

    private enum Step {
        START, REFILL, SCAN, NEXT_VILLAGER, NAVIGATE, INTERACT, WAIT_AIM, WAIT_SCREEN, WAIT_OFFERS, SELECT_TRADE, WAIT_RESULT,
        TAKE_RESULT, WAIT_TAKE, PLACE_RESULT, WAIT_PLACE, VERIFY_TRADE,
        CLOSE, WAIT_CLOSE, FINISH, DONE
    }

    private Step step = Step.START;
    private int ticks;
    private int selectRetries;
    private int aimAttempts;

    private final List<Villager> farmers = new ArrayList<>();
    private Villager current;
    private int offerIndex;
    private int cost;
    private int resultCount;
    private int carrotsBefore;
    private int emeraldsBefore;
    private int targetSlot;
    private int tradesDone;
    private int villagerTrades;
    private int farmerIndex;
    private Step afterClose = Step.FINISH;
    private Step afterRefill = Step.SCAN;
    private int refills;
    private String finishMessage;
    private String lastInteraction = "-";
    private long tickCounter;
    private long lastSelectTick = -1;

    private final RefillController refill = new RefillController();
    private final LocalNavigation navigation = new LocalNavigation();

    /** Stoğu bitmiş / uygun takası olmayan köylüler. Shop gidiş-gelişiyle silinmez; yalnızca reset()'te sıfırlanır. */
    private final Set<UUID> exhausted = new HashSet<>();

    public void reset() {
        navigation.stop();
        step = Step.START;
        ticks = 0;
        selectRetries = 0;
        aimAttempts = 0;
        farmers.clear();
        current = null;
        offerIndex = -1;
        cost = 0;
        resultCount = 0;
        carrotsBefore = 0;
        emeraldsBefore = 0;
        targetSlot = -1;
        tradesDone = 0;
        villagerTrades = 0;
        farmerIndex = 0;
        afterClose = Step.FINISH;
        afterRefill = Step.SCAN;
        refills = 0;
        finishMessage = null;
        exhausted.clear();
        refill.reset();
        tickCounter = 0;
        lastSelectTick = -1;
    }

    public void pause() {
        if (step == Step.NAVIGATE) {
            navigation.stop();
            chat(Minecraft.getInstance(), "Yol bulma duraklatıldı.");
        }
    }

    public boolean resume(Minecraft client) {
        if (step != Step.NAVIGATE) return true;
        if (current == null || !current.isAlive()) {
            AutomationManager.INSTANCE.fail("Devam edilemedi: seçilen çiftçi artık mevcut değil.");
            return false;
        }
        if (!navigation.start(client, current)) {
            AutomationManager.INSTANCE.fail("Yol bulma devam ettirilemedi: " + navigation.getLastError());
            return false;
        }
        chat(client, "Köylüye giden rota yeniden hesaplandı.");
        return true;
    }

    public boolean tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return step == Step.DONE;
        }
        if (step == Step.DONE) return true;

        tickCounter++;

        try {
            switch (step) {
                case START -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        return fail(client, "Başlangıçta açık bir konteyner menüsü var.");
                    }
                    if (InventoryManager.countCarrots(client) < RefillController.TARGET_CARROTS) {
                        refill.reset();
                        afterRefill = Step.SCAN;
                        step = Step.REFILL;
                    } else {
                        step = Step.SCAN;
                    }
                }
                case REFILL -> {
                    RefillController.Result result = refill.tick(client);
                    if (result == RefillController.Result.FAILED) {
                        return fail(client, refill.getError() == null ? "Shop alımı başarısız." : refill.getError());
                    }
                    if (result == RefillController.Result.RUNNING) return false;

                    int carrots = InventoryManager.countCarrots(client);
                    AutomationManager.INSTANCE.setCarrotsInInventory(carrots);
                    refills++;

                    if (refill.wasLimitedByCapacity()) {
                        finishMessage = "Envanterde yer yetmediği için Shop'ta daha az havuç alındı (" + carrots
                                + " havuç). Döngü güvenli şekilde durduruldu; envanterde yer aç ve tekrar başlat.";
                        step = Step.FINISH;
                        return false;
                    }
                    if (afterRefill == Step.SCAN) {
                        if (carrots <= 0) return fail(client, "Shop alımından sonra envanterde havuç yok.");
                        step = Step.SCAN;
                        return false;
                    }
                    if (carrots < cost) {
                        return fail(client, "Shop alımından sonra bile havuç yetmiyor: " + carrots + " var, takas "
                                + cost + " istiyor.");
                    }
                    step = Step.INTERACT;
                }
                case SCAN -> {
                    farmers.clear();
                    farmers.addAll(FarmerScanner.scanFarmers(client));
                    AutomationManager.INSTANCE.setVillagerCount(farmers.size());
                    if (farmers.isEmpty()) return fail(client, "Tarama alanında canlı çiftçi bulunamadı.");
                    farmerIndex = -1;
                    chat(client, farmers.size() + " çiftçi bulundu (en yakından uzağa); bağımsız yol bulma kullanılacak.");
                    step = Step.NEXT_VILLAGER;
                }
                case NEXT_VILLAGER -> {
                    farmerIndex++;
                    while (farmerIndex < farmers.size() && exhausted.contains(farmers.get(farmerIndex).getUUID())) {
                        farmerIndex++;
                    }
                    if (farmerIndex >= farmers.size()) {
                        finishMessage = "Döngü bitti: " + tradesDone + " takas yapıldı, sıradaki çiftçi kalmadı.";
                        step = Step.FINISH;
                        return false;
                    }
                    current = farmers.get(farmerIndex);
                    villagerTrades = 0;
                    AutomationManager.INSTANCE.setCurrentVillagerIndex(farmerIndex);
                    chat(client, "Çiftçi " + (farmerIndex + 1) + "/" + farmers.size() + " ile devam ediliyor.");
                    if (FarmerScanner.isAccessible(client, current)) {
                        step = Step.INTERACT;
                    } else {
                        if (!navigation.start(client, current)) {
                            return fail(client, "Çiftçiye güvenli yol başlatılamadı: " + navigation.getLastError());
                        }
                        step = Step.NAVIGATE;
                        chat(client, "Köylüye bağımsız yol bulma ile gidiliyor. Blok kırma/yerleştirme kapalı.");
                    }
                }
                case NAVIGATE -> {
                    if (current == null || !current.isAlive()) {
                        return fail(client, "Yol bulunurken seçilen çiftçi artık mevcut değil.");
                    }
                    LocalNavigation.Status status = navigation.tick(client, current);
                    if (status == LocalNavigation.Status.REACHED) {
                        chat(client, "Köylüye ulaşıldı; kamera yüzüne çevrildi, takas ekranı açılıyor.");
                        step = Step.INTERACT;
                        return false;
                    }
                    if (status == LocalNavigation.Status.FAILED) {
                        return fail(client, "Köylüye ulaşılamadı: " + navigation.getLastError());
                    }
                }
                case INTERACT -> {
                    if (!FarmerScanner.isAccessible(client, current)) {
                        exhausted.add(current.getUUID());
                        chat(client, "Çiftçi erişilemez oldu; atlanıyor.");
                        step = Step.NEXT_VILLAGER;
                        return false;
                    }
                    lastSelectTick = -1;
                    aimAttempts = 0;
                    LocalNavigation.faceEntity(client, current);
                    ticks = 0;
                    step = Step.WAIT_AIM;
                }
                case WAIT_AIM -> {
                    if (current == null || !current.isAlive()) {
                        return fail(client, "Etkileşimden önce seçilen çiftçi artık mevcut değil.");
                    }
                    // Kamera açıları değişince Minecraft'ın crosshair hitResult değeri aynı
                    // tick içinde güncellenmeyebilir. En az iki client tick bekle.
                    if (++ticks < 2) return false;

                    if (client.hitResult instanceof EntityHitResult entityHit
                            && entityHit.getEntity() == current) {
                        InteractionResult interaction = client.gameMode.interact(
                                client.player, current, InteractionHand.MAIN_HAND);
                        lastInteraction = String.valueOf(interaction);
                        chat(client, "Nişangâh doğrulandı: hedef çiftçi, mesafe "
                                + String.format(java.util.Locale.ROOT, "%.2f", client.player.distanceTo(current))
                                + " blok. Etkileşim gönderildi: " + lastInteraction);
                        ticks = 0;
                        step = Step.WAIT_SCREEN;
                        return false;
                    }

                    if (aimAttempts < 2) {
                        aimAttempts++;
                        LocalNavigation.faceEntity(client, current);
                        ticks = 0;
                        chat(client, "Nişangâh hedefi henüz köylü değil; kamera yeniden hizalanıyor ("
                                + aimAttempts + "/2).");
                        return false;
                    }

                    String hit = client.hitResult == null ? "yok"
                            : client.hitResult.getType().name()
                            + (client.hitResult instanceof EntityHitResult entityHit
                                ? " / " + entityHit.getEntity().getType().toString() : "");
                    return fail(client, "Köylüye etkileşim gönderilmedi: nişangâh köylüyü hedeflemiyor"
                            + " (hedef=" + hit
                            + ", mesafe=" + String.format(java.util.Locale.ROOT, "%.2f", client.player.distanceTo(current))
                            + ", menzil/görüş=" + FarmerScanner.isAccessible(client, current) + ").");
                }
                case WAIT_SCREEN -> {
                    if (client.screen instanceof MerchantScreen) {
                        ticks = 0;
                        step = Step.WAIT_OFFERS;
                    } else if (client.screen instanceof AbstractContainerScreen<?> other) {
                        return fail(client, "Çiftçi takas menüsü yerine farklı bir menü açtı: "
                                + other.getClass().getSimpleName() + " / başlık: " + other.getTitle().getString());
                    } else if (++ticks > SCREEN_TIMEOUT) {
                        return fail(client, "Takas menüsü zamanında açılmadı (etkileşim sonucu: " + lastInteraction
                                + ", ekran: " + (client.screen == null ? "yok" : client.screen.getClass().getSimpleName()) + ").");
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
                    if (!locateOffer(client, menu)) return false;
                    step = Step.SELECT_TRADE;
                }
                case SELECT_TRADE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü beklenmedik şekilde kapandı.");
                    }
                    if (lastSelectTick >= 0
                            && tickCounter - lastSelectTick < Math.max(1, AutomationConfig.get().tradeIntervalTicks)) {
                        return false;
                    }
                    int carrots = carrotsTotal(client, screen.getMenu());
                    if (villagerTrades >= MAX_TRADES_PER_VILLAGER) {
                        villagerDone(client, "güvenlik sınırı (" + MAX_TRADES_PER_VILLAGER + " takas) doldu");
                        return false;
                    }
                    if (carrots < cost) {
                        notEnoughCarrots(client, carrots);
                        return false;
                    }
                    carrotsBefore = carrots;
                    selectRetries = 0;
                    emeraldsBefore = InventoryManager.countEmeralds(client);
                    sendSelect(client, screen.getMenu());
                    lastSelectTick = tickCounter;
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
                    if (ticks >= 1 && !result.isEmpty() && result.is(Items.EMERALD) && result.getCount() == resultCount) {
                        step = Step.TAKE_RESULT;
                        return false;
                    }
                    if (ticks > RESULT_TIMEOUT) {
                        if (++selectRetries > MAX_SELECT_RETRIES) {
                            String reason = diagnoseNoResult(client, menu);
                            if (reason == null) {
                                villagerDone(client, "stok bitti ya da takas kilitli (sunucu sonuç vermedi)");
                                return false;
                            }
                            return fail(client, reason);
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
                    if (targetSlot < 0) return fail(client, "Envanterde zümrüt için yer yok; takas sonucu alınmadı.");
                    client.gameMode.handleInventoryMouseClick(menu.containerId, RESULT_SLOT, 0, ClickType.PICKUP, client.player);
                    ticks = 0;
                    ItemStack carriedNow = menu.getCarried();
                    if (!carriedNow.isEmpty() && carriedNow.is(Items.EMERALD)) {
                        client.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, client.player);
                        step = Step.WAIT_PLACE;
                    } else {
                        step = Step.WAIT_TAKE;
                    }
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
                        return fail(client, "Zümrüt envantere yerleştirilemedi (imleçte kaldı).");
                    }
                }
                case VERIFY_TRADE -> {
                    if (!(client.screen instanceof MerchantScreen verifyScreen)) {
                        return fail(client, "Takas menüsü doğrulama sırasında kapandı.");
                    }
                    int carrots = carrotsTotal(client, verifyScreen.getMenu());
                    int emeralds = InventoryManager.countEmeralds(client);
                    boolean carrotsOk = carrots == carrotsBefore - cost;
                    boolean emeraldsOk = emeralds == emeraldsBefore + resultCount;
                    if (carrotsOk && emeraldsOk) {
                        tradesDone++;
                        villagerTrades++;
                        chat(client, "Takas doğrulandı: havuç " + carrotsBefore + " -> " + carrots
                                + ", zümrüt " + emeraldsBefore + " -> " + emeralds + ".");
                        AutomationManager.INSTANCE.addEmeralds(resultCount);
                        ticks = 0;
                        selectRetries = 0;
                        step = Step.SELECT_TRADE;
                    } else if (++ticks > VERIFY_TIMEOUT) {
                        return fail(client, "Takas doğrulanamadı: havuç " + carrotsBefore + " -> " + carrots
                                + " (beklenen " + (carrotsBefore - cost) + "), zümrüt " + emeraldsBefore + " -> "
                                + emeralds + " (beklenen " + (emeraldsBefore + resultCount) + ").");
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
                        if (ticks >= CLOSE_WAIT) step = afterClose;
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
                case DONE -> { return true; }
            }
        } catch (Exception exception) {
            return fail(client, "Döngü hatası: " + exception.getClass().getSimpleName());
        }

        return step == Step.DONE;
    }

    private boolean locateOffer(Minecraft client, MerchantMenu menu) {
        int index = 0;
        for (MerchantOffer offer : menu.getOffers()) {
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult();

            if (costA.is(Items.CARROT) && costB.isEmpty() && result.is(Items.EMERALD)) {
                if (offer.isOutOfStock()) {
                    villagerDone(client, "havuç -> zümrüt stoğu bitmiş");
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
        villagerDone(client, "havuç -> zümrüt takası yok");
        return false;
    }

    private void villagerDone(Minecraft client, String reason) {
        if (current != null) exhausted.add(current.getUUID());
        chat(client, "Çiftçi " + (farmerIndex + 1) + " bitti (" + reason + "); " + villagerTrades + " takas yapıldı.");
        afterClose = Step.NEXT_VILLAGER;
        step = Step.CLOSE;
    }

    private void notEnoughCarrots(Minecraft client, int carrots) {
        if (refills >= MAX_REFILLS) {
            finishMessage = "Shop'a dönüş sınırına ulaşıldı (" + MAX_REFILLS + "); " + tradesDone + " takas yapıldı.";
            afterClose = Step.FINISH;
            step = Step.CLOSE;
            return;
        }
        chat(client, "Havuç bir sonraki takasa yetmiyor (" + carrots + " var, " + cost
                + " gerekli); Shop'a dönülüyor. Çiftçi " + (farmerIndex + 1) + " ile devam edilecek.");
        refill.reset();
        afterRefill = Step.INTERACT;
        afterClose = Step.REFILL;
        step = Step.CLOSE;
    }

    private static int carrotsTotal(Minecraft client, MerchantMenu menu) {
        int total = InventoryManager.countCarrots(client);
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.is(Items.CARROT)) total += stack.getCount();
        }
        return total;
    }

    private void sendSelect(Minecraft client, MerchantMenu menu) {
        menu.setSelectionHint(offerIndex);
        menu.tryMoveItems(offerIndex);
        client.player.connection.send(new ServerboundSelectTradePacket(offerIndex));
    }

    private String diagnoseNoResult(Minecraft client, MerchantMenu menu) {
        ItemStack payment = menu.getSlot(PAYMENT_SLOT_A).getItem();
        int carrots = carrotsTotal(client, menu);

        if (payment.is(Items.CARROT) && payment.getCount() >= cost) {
            return null; // havuç yerleşti ama sonuç yok: stok bitti / kilitli
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
        navigation.stop();
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
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6[Döngü]§r " + message), false);
        }
    }
}
