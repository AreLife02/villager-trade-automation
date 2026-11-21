package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import com.example.farmerautomation.trade.OfferInfo;
import com.example.farmerautomation.trade.OfferReader;
import com.example.farmerautomation.trade.TradeInventory;
import com.example.farmerautomation.trade.TradeItem;
import com.example.farmerautomation.trade.TradeProfession;
import com.example.farmerautomation.trade.TradeRule;
import com.example.farmerautomation.trade.TradeSelector;
import com.example.farmerautomation.trade.VillagerScanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Meslek ve takas kurallarına göre köylü takas döngüsü (çiftçi havuç -> zümrüt varsayılan kuraldır).
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

    private static final int RESULT_SLOT = 2;
    private static final String CARROT_ID = "minecraft:carrot";

    private enum Step {
        START, REFILL, NEXT_PROFESSION, SCAN, NEXT_VILLAGER, NAVIGATE, INTERACT, WAIT_AIM, WAIT_SCREEN, WAIT_OFFERS,
        DECIDE, SELECT_TRADE, WAIT_RESULT,
        TAKE_RESULT, WAIT_TAKE, PLACE_RESULT, WAIT_PLACE, VERIFY_TRADE,
        CLOSE, WAIT_CLOSE, RESCAN_WAIT, STORE, FINISH, DONE
    }

    private Step step = Step.START;
    private int ticks;
    private int selectRetries;
    private int aimAttempts;

    private final List<Villager> villagers = new ArrayList<>();
    private Villager current;
    private int offerIndex;
    /** Shop yenilemesinden sonra bir sonraki takas için gereken havuç adedi. */
    private int cost;
    private OfferInfo activeOffer;
    private TradeRule activeRule;
    private Map<String, Integer> countsBefore = new HashMap<>();
    private ItemStack resultStack = ItemStack.EMPTY;
    private int targetSlot;
    private int tradesDone;
    private int villagerTrades;
    private int villagerIndex;
    private List<TradeProfession> activeProfessions = new ArrayList<>();
    private int professionIndex;
    private TradeProfession currentProfession;
    private boolean foundAny;
    private final Map<String, Integer> tradesByRule = new HashMap<>();
    /** Bu köylü ziyaretinde sonuç vermeyen (stok bitmiş/kilitli) teklif indeksleri. */
    private final Set<Integer> blockedOffers = new HashSet<>();
    private Step afterClose = Step.FINISH;
    private Step afterRefill = Step.SCAN;
    private int refills;
    private String finishMessage;
    private String lastInteraction = "-";
    private long tickCounter;
    private long lastSelectTick = -1;

    private final RefillController refill = new RefillController();
    private final StorageController storage = new StorageController();
    private Step afterStore = Step.NEXT_VILLAGER;
    private final LocalNavigation navigation = new LocalNavigation();

    /** Stoğu bitmiş / uygun takası olmayan köylüler. Shop gidiş-gelişiyle silinmez; yalnızca reset()'te sıfırlanır. */
    private final Set<UUID> exhausted = new HashSet<>();

    public void reset() {
        navigation.stop();
        step = Step.START;
        ticks = 0;
        selectRetries = 0;
        aimAttempts = 0;
        villagers.clear();
        current = null;
        offerIndex = -1;
        cost = 0;
        activeOffer = null;
        activeRule = null;
        countsBefore = new HashMap<>();
        resultStack = ItemStack.EMPTY;
        targetSlot = -1;
        tradesDone = 0;
        villagerTrades = 0;
        villagerIndex = 0;
        activeProfessions = new ArrayList<>();
        professionIndex = -1;
        currentProfession = null;
        foundAny = false;
        tradesByRule.clear();
        blockedOffers.clear();
        afterClose = Step.FINISH;
        afterRefill = Step.NEXT_PROFESSION;
        refills = 0;
        finishMessage = null;
        exhausted.clear();
        refill.reset();
        storage.reset();
        afterStore = Step.NEXT_VILLAGER;
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
            AutomationManager.INSTANCE.fail("Devam edilemedi: seçilen köylü artık mevcut değil.");
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
                    activeProfessions = resolveProfessions();
                    if (activeProfessions.isEmpty()) {
                        return fail(client, "Etkin takas kuralı olan meslek yok. Ayar dosyasında professionOrder ve "
                                + "tradeRules (enabled=true) bölümlerini kontrol et.");
                    }
                    professionIndex = -1;
                    foundAny = false;
                    // Shop yalnızca çiftçi havuç -> zümrüt kuralı etkinse gerekir; diğer takaslar için havuç alınmaz.
                    if (needsCarrotShop() && InventoryManager.countCarrots(client) < RefillController.TARGET_CARROTS) {
                        refill.reset();
                        afterRefill = Step.NEXT_PROFESSION;
                        step = Step.REFILL;
                    } else {
                        step = Step.NEXT_PROFESSION;
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
                    if (afterRefill == Step.NEXT_PROFESSION) {
                        if (carrots <= 0) return fail(client, "Shop alımından sonra envanterde havuç yok.");
                        step = Step.NEXT_PROFESSION;
                        return false;
                    }
                    if (carrots < cost) {
                        return fail(client, "Shop alımından sonra bile havuç yetmiyor: " + carrots + " var, takas "
                                + cost + " istiyor.");
                    }
                    step = Step.INTERACT;
                }
                case NEXT_PROFESSION -> {
                    professionIndex++;
                    if (professionIndex >= activeProfessions.size()) {
                        return endOfPass(client);
                    }
                    currentProfession = activeProfessions.get(professionIndex);
                    step = Step.SCAN;
                }
                case SCAN -> {
                    villagers.clear();
                    // Bu geliştirme aşamasında yalnızca çiftçi köylüler işlenir.
                    // Kullanıcı seçimi/meslek sırası, uygun çiftçilerin taranmasını engellemez.
                    villagers.addAll(FarmerScanner.scanFarmers(client));
                    AutomationManager.INSTANCE.setVillagerCount(villagers.size());
                    if (villagers.isEmpty()) {
                        // Bir meslekte köylü yoksa sonsuza kadar bekleme; sıradaki mesleğe geç.
                        chat(client, "Tarama alanında " + currentProfession.displayName()
                                + " bulunamadı; sıradaki mesleğe geçiliyor.");
                        step = Step.NEXT_PROFESSION;
                        return false;
                    }
                    foundAny = true;
                    villagerIndex = -1;
                    chat(client, villagers.size() + " " + currentProfession.displayName()
                            + " bulundu (en yakından uzağa); bağımsız yol bulma kullanılacak.");
                    step = Step.NEXT_VILLAGER;
                }
                case NEXT_VILLAGER -> {
                    // Her köylüden sonra kalan hedefleri oyuncunun GÜNCEL konumuna
                    // göre yeniden sırala. Dikey fark için hafif ek maliyet, gereksiz
                    // kat değişimlerini azaltır; ulaşılabilirlik yine A* tarafından belirlenir.
                    int remainingStart = Math.min(villagers.size(), Math.max(0, villagerIndex + 1));
                    if (remainingStart < villagers.size()) {
                        villagers.subList(remainingStart, villagers.size()).sort((first, second) ->
                                Double.compare(routePriority(client, first), routePriority(client, second)));
                    }

                    villagerIndex++;
                    while (villagerIndex < villagers.size()
                            && (exhausted.contains(villagers.get(villagerIndex).getUUID())
                            || !villagers.get(villagerIndex).isAlive())) {
                        villagerIndex++;
                    }
                    if (villagerIndex >= villagers.size()) {
                        // Bu mesleğin köylüleri bitti: kullanıcının sıradaki mesleğine geç.
                        step = Step.NEXT_PROFESSION;
                        return false;
                    }
                    current = villagers.get(villagerIndex);
                    villagerTrades = 0;
                    tradesByRule.clear();
                    blockedOffers.clear();
                    AutomationManager.INSTANCE.setCurrentVillagerIndex(villagerIndex);
                    chat(client, currentProfession.displayName() + " " + (villagerIndex + 1) + "/" + villagers.size()
                            + " ile devam ediliyor.");
                    if (FarmerScanner.isAccessible(client, current)) {
                        step = Step.INTERACT;
                    } else {
                        if (!navigation.start(client, current)) {
                            exhausted.add(current.getUUID());
                            chat(client, "Köylüye güvenli yol bulunamadı; bu köylü atlanıyor: "
                                    + navigation.getLastError());
                            navigation.stop();
                            step = Step.NEXT_VILLAGER;
                            return false;
                        }
                        step = Step.NAVIGATE;
                        chat(client, "Köylüye bağımsız yol bulma ile gidiliyor. Blok kırma/yerleştirme kapalı.");
                    }
                }
                case NAVIGATE -> {
                    if (current == null || !current.isAlive()) {
                        if (current != null) exhausted.add(current.getUUID());
                        chat(client, "Hedef köylü artık mevcut değil; sıradaki köylüye geçiliyor.");
                        navigation.stop();
                        step = Step.NEXT_VILLAGER;
                        return false;
                    }
                    LocalNavigation.Status status = navigation.tick(client, current);
                    if (status == LocalNavigation.Status.REACHED) {
                        chat(client, "Köylüye ulaşıldı; kamera yüzüne çevrildi, takas ekranı açılıyor.");
                        step = Step.INTERACT;
                        return false;
                    }
                    if (status == LocalNavigation.Status.FAILED) {
                        exhausted.add(current.getUUID());
                        chat(client, "Köylüye ulaşılamadı; bu köylü atlanıyor: " + navigation.getLastError());
                        navigation.stop();
                        step = Step.NEXT_VILLAGER;
                        return false;
                    }
                }
                case INTERACT -> {
                    if (!FarmerScanner.isAccessible(client, current)) {
                        exhausted.add(current.getUUID());
                        chat(client, "Köylü erişilemez oldu; atlanıyor.");
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
                        return fail(client, "Etkileşimden önce seçilen köylü artık mevcut değil.");
                    }
                    // Kamera açıları değişince Minecraft'ın crosshair hitResult değeri aynı
                    // tick içinde güncellenmeyebilir. En az iki client tick bekle.
                    if (++ticks < 2) return false;

                    if (client.hitResult instanceof EntityHitResult entityHit
                            && entityHit.getEntity() == current) {
                        // Minecraft'ın normal sağ tık akışı: önce hit konumuyla interactAt,
                        // sonuç eylemi tüketmiyorsa standart interact paketine geç.
                        VillagerInteraction.Attempt attempt = VillagerInteraction.interact(client, current, entityHit);
                        lastInteraction = attempt.describe()
                                + " / anaEl=" + client.player.getMainHandItem().getItem()
                                + " / yanEl=" + client.player.getOffhandItem().getItem();
                        chat(client, "Nişangâh doğrulandı: hedef köylü, mesafe "
                                + String.format(java.util.Locale.ROOT, "%.2f", client.player.distanceTo(current))
                                + " blok. Vanilla etkileşim akışı gönderildi: " + lastInteraction);
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
                        return fail(client, "Köylü takas menüsü yerine farklı bir menü açtı: "
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
                    if (VillagerScanner.professionOf(current) != currentProfession) {
                        villagerDone(client, "köylünün mesleği değişmiş");
                        return false;
                    }
                    step = Step.DECIDE;
                }
                case DECIDE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü beklenmedik şekilde kapandı.");
                    }
                    MerchantMenu menu = screen.getMenu();
                    if (villagerTrades >= MAX_TRADES_PER_VILLAGER) {
                        villagerDone(client, "güvenlik sınırı (" + MAX_TRADES_PER_VILLAGER + " takas) doldu");
                        return false;
                    }
                    List<OfferInfo> offers = OfferReader.read(menu.getOffers());
                    Map<String, Integer> available = TradeInventory.snapshot(client, menu);
                    TradeSelector.Decision decision = TradeSelector.decide(currentProfession.id(), offers,
                            enabledCarrotRules(), tradesByRule, blockedOffers, available,
                            CycleController::isCarrotRefillRule);
                    if (decision instanceof TradeSelector.Trade trade) {
                        activeOffer = trade.offer();
                        activeRule = trade.rule();
                        offerIndex = activeOffer.index();
                        AutomationManager.INSTANCE.setCurrentTradeStockRemaining(
                                Math.max(0, activeOffer.maxUses() - activeOffer.uses()));
                        step = Step.SELECT_TRADE;
                    } else if (decision instanceof TradeSelector.Refill refillDecision) {
                        cost = refillDecision.offer().primary().count();
                        AutomationManager.INSTANCE.setCurrentTradeStockRemaining(
                                Math.max(0, refillDecision.offer().maxUses() - refillDecision.offer().uses()));
                        notEnoughCarrots(client, available.getOrDefault(CARROT_ID, 0));
                    } else if (decision instanceof TradeSelector.Done done) {
                        AutomationManager.INSTANCE.setCurrentTradeStockRemaining(0);
                        villagerDone(client, done.kind().message());
                    }
                }
                case SELECT_TRADE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü beklenmedik şekilde kapandı.");
                    }
                    if (lastSelectTick >= 0
                            && tickCounter - lastSelectTick < Math.max(1, AutomationConfig.get().tradeIntervalTicks)) {
                        return false;
                    }
                    countsBefore = TradeInventory.snapshot(client, screen.getMenu());
                    selectRetries = 0;
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
                    TradeItem output = activeOffer.output();
                    ticks++;
                    if (ticks >= 1 && !result.isEmpty() && OfferReader.baseId(result).equals(output.baseId())
                            && result.getCount() == output.count()) {
                        resultStack = result.copy();
                        step = Step.TAKE_RESULT;
                        return false;
                    }
                    if (ticks > RESULT_TIMEOUT) {
                        if (++selectRetries > MAX_SELECT_RETRIES) {
                            String reason = diagnoseNoResult(client, menu);
                            if (reason == null) {
                                // Giriş eşyaları yerleşti ama sonuç yok: bu teklifin stoğu bitti ya da kilitli.
                                // Önce aynı köylüdeki sıradaki uygun takasa geç.
                                blockedOffers.add(offerIndex);
                                chat(client, "Teklif " + (offerIndex + 1) + " sonuç vermedi (stok bitti ya da kilitli); "
                                        + "sıradaki takasa geçiliyor.");
                                ticks = 0;
                                step = Step.DECIDE;
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
                    if (targetSlot < 0) {
                        return fail(client, "Envanterde " + activeOffer.output().name()
                                + " için yer yok; takas sonucu alınmadı.");
                    }
                    client.gameMode.handleInventoryMouseClick(menu.containerId, RESULT_SLOT, 0, ClickType.PICKUP, client.player);
                    ticks = 0;
                    // Sonuç slotundan alma ve envantere bırakma tıklamalarını ayrı
                    // client tick'lerine böl. Aynı tick'te iki tıklama bazı sunucularda
                    // menünün kapanmasına veya ikinci tıklamanın yanlış menüye gitmesine yol açabiliyor.
                    step = Step.WAIT_TAKE;
                }
                case WAIT_TAKE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü sonuç alınırken kapandı.");
                    }
                    ItemStack carried = screen.getMenu().getCarried();
                    if (!carried.isEmpty() && OfferReader.baseId(carried).equals(activeOffer.output().baseId())) {
                        step = Step.PLACE_RESULT;
                    } else if (++ticks > CLICK_TIMEOUT) {
                        return fail(client, "Sonuç slotundan " + activeOffer.output().name() + " alınamadı.");
                    }
                }
                case PLACE_RESULT -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü sonuç yerleştirilirken kapandı.");
                    }
                    MerchantMenu menu = screen.getMenu();
                    client.gameMode.handleInventoryMouseClick(menu.containerId, targetSlot, 0, ClickType.PICKUP, client.player);
                    ticks = 0;
                    step = Step.WAIT_PLACE;
                }
                case WAIT_PLACE -> {
                    if (!(client.screen instanceof MerchantScreen screen)) {
                        return fail(client, "Takas menüsü sonuç yerleştirilirken kapandı.");
                    }
                    if (screen.getMenu().getCarried().isEmpty()) {
                        ticks = 0;
                        step = Step.VERIFY_TRADE;
                    } else if (++ticks > CLICK_TIMEOUT) {
                        return fail(client, activeOffer.output().name() + " envantere yerleştirilemedi (imleçte kaldı).");
                    }
                }
                case VERIFY_TRADE -> {
                    if (!(client.screen instanceof MerchantScreen verifyScreen)) {
                        return fail(client, "Takas menüsü doğrulama sırasında kapandı.");
                    }
                    Map<String, Integer> after = TradeInventory.snapshot(client, verifyScreen.getMenu());
                    String mismatch = null;
                    for (Map.Entry<String, Integer> expected : activeOffer.expectedDelta().entrySet()) {
                        int before = countsBefore.getOrDefault(expected.getKey(), 0);
                        int now = after.getOrDefault(expected.getKey(), 0);
                        if (now != before + expected.getValue()) {
                            mismatch = expected.getKey() + " " + before + " -> " + now
                                    + " (beklenen " + (before + expected.getValue()) + ")";
                            break;
                        }
                    }
                    if (mismatch == null) {
                        tradesDone++;
                        villagerTrades++;
                        tradesByRule.merge(activeRule.key(), 1, Integer::sum);
                        // Per-trade inventory deltas are intentionally kept out of chat.
                        // Villager completion reports the compact trade count instead.
                        if (activeOffer.direction() == com.example.farmerautomation.trade.TradeDirection.EARN) {
                            AutomationManager.INSTANCE.addEmeralds(activeOffer.output().count());
                        }
                        ticks = 0;
                        selectRetries = 0;
                        AutomationManager.INSTANCE.setCarrotsInInventory(InventoryManager.countCarrots(client));
                        step = Step.DECIDE;
                    } else if (++ticks > VERIFY_TIMEOUT) {
                        return fail(client, "Takas doğrulanamadı: " + mismatch + ".");
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
                case STORE -> {
                    StorageController.Result result = storage.tick(client);
                    if (result == StorageController.Result.FAILED) {
                        String reason = storage.getError() == null
                                ? "Depolama işlemi tamamlanamadı." : storage.getError();

                        // Depolama isteğe bağlıdır: hata yalnızca depolama adımını
                        // sonlandırmalı, ana köylü takas döngüsünü durdurmamalıdır.
                        if (client.screen instanceof AbstractContainerScreen<?>) {
                            client.player.closeContainer();
                        }
                        chat(client, "Depolama atlandı: " + reason
                                + " Zümrütler envanterde bırakıldı; otomasyon devam ediyor.");
                        storage.reset();
                        step = afterStore;
                        return false;
                    }
                    if (result == StorageController.Result.DONE) {
                        storage.reset();
                        step = afterStore;
                    }
                }
                case RESCAN_WAIT -> {
                    if (++ticks >= 40) {
                        ticks = 0;
                        step = Step.NEXT_PROFESSION;
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

    private static double routePriority(Minecraft client, Villager villager) {
        double verticalDifference = Math.abs(client.player.getY() - villager.getY());
        return FarmerScanner.reachDistance(client, villager) + verticalDifference * 0.25;
    }

    private List<TradeProfession> resolveProfessions() {
        // Bu sürüm yalnızca etkin havuç -> zümrüt çiftçi takasını çalıştırır.
        return enabledCarrotRules().isEmpty()
                ? List.of()
                : List.of(TradeProfession.FARMER);
    }

    private static List<TradeRule> enabledCarrotRules() {
        List<TradeRule> configured = AutomationConfig.get().tradeRules;
        if (configured == null) return List.of();
        return configured.stream()
                .filter(rule -> rule != null && rule.enabled && isCarrotRefillRule(rule))
                .toList();
    }

    public UUID getCurrentVillagerId() {
        return current == null ? null : current.getUUID();
    }

    /** Shop havuç alımı yalnızca etkin çiftçi havuç -> zümrüt kuralı varsa gerekir. */
    private boolean needsCarrotShop() {
        return activeProfessions.contains(TradeProfession.FARMER) && !enabledCarrotRules().isEmpty();
    }

    private static boolean isCarrotRefillRule(TradeRule rule) {
        return rule != null
                && TradeProfession.FARMER.id().equalsIgnoreCase(rule.profession)
                && com.example.farmerautomation.trade.TradeDirection.EARN.name().equals(rule.direction)
                && CARROT_ID.equals(rule.itemId);
    }

    /** Bütün meslekler tarandı: sınırsız döngüde yeniden başla, değilse bitir. */
    private boolean endOfPass(Minecraft client) {
        if (AutomationConfig.get().unlimitedCycle) {
            exhausted.clear();
            professionIndex = -1;
            ticks = 0;
            step = Step.RESCAN_WAIT;
            if (foundAny) {
                chat(client, "Seçili meslekler tamamlandı (" + tradesDone
                        + " takas). Sınırsız döngü için yeniden taranıyor.");
            } else {
                chat(client, "Tarama alanında seçili mesleklerden köylü bulunamadı; 2 saniye sonra yeniden taranacak.");
            }
            foundAny = false;
            return false;
        }
        if (!foundAny) {
            return fail(client, "Tarama alanında seçili mesleklerden canlı köylü bulunamadı.");
        }
        finishMessage = "Döngü bitti: " + tradesDone + " takas yapıldı, sıradaki köylü kalmadı.";
        afterStore = Step.FINISH;
        step = Step.STORE;
        return false;
    }

    private void villagerDone(Minecraft client, String reason) {
        if (current != null) exhausted.add(current.getUUID());
        chat(client, currentProfession.displayName() + " " + (villagerIndex + 1) + " bitti (" + reason + "); "
                + villagerTrades + " takas yapıldı.");

        // Depolamaya her köylüden sonra gitme. Yalnızca envanter neredeyse
        // dolduğunda, köylü menüsü kapandıktan sonra zümrütleri boşalt.
        afterClose = shouldStoreEmeralds(client) ? Step.STORE : Step.NEXT_VILLAGER;
        afterStore = Step.NEXT_VILLAGER;
        storage.reset();
        step = Step.CLOSE;
    }

    private static boolean shouldStoreEmeralds(Minecraft client) {
        AutomationConfig config = AutomationConfig.get();
        if (!config.storageEnabled || InventoryManager.countEmeralds(client) <= 0) return false;

        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) emptySlots++;
        }
        // İki veya daha az boş slot kaldığında depolamayı tetikle.
        return emptySlots <= 2;
    }

    private void notEnoughCarrots(Minecraft client, int carrots) {
        if (refills >= MAX_REFILLS) {
            finishMessage = "Shop'a dönüş sınırına ulaşıldı (" + MAX_REFILLS + "); " + tradesDone + " takas yapıldı.";
            afterClose = Step.FINISH;
            step = Step.CLOSE;
            return;
        }
        chat(client, "Havuç bir sonraki takasa yetmiyor (" + carrots + " var, " + cost
                + " gerekli); Shop'a dönülüyor. " + currentProfession.displayName() + " " + (villagerIndex + 1)
                + " ile devam edilecek.");
        refill.reset();
        afterRefill = Step.INTERACT;
        afterClose = Step.REFILL;
        step = Step.CLOSE;
    }

    private void sendSelect(Minecraft client, MerchantMenu menu) {
        menu.setSelectionHint(offerIndex);
        menu.tryMoveItems(offerIndex);
        client.player.connection.send(new ServerboundSelectTradePacket(offerIndex));
    }

    /**
     * Sonuç gelmediğinde nedeni bulur. null: giriş eşyaları takas slotlarına yerleşmiş, yani teklifin stoğu
     * bitmiş ya da kilitli. Aksi halde durdurmayı gerektiren bir hata mesajı döner.
     */
    private String diagnoseNoResult(Minecraft client, MerchantMenu menu) {
        List<TradeItem> inputs = activeOffer.inputs();
        boolean allPlaced = true;
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack placed = menu.getSlot(slot).getItem();
            TradeItem needed = inputs.get(slot);
            if (placed.isEmpty() || !OfferReader.baseId(placed).equals(needed.baseId())
                    || placed.getCount() < needed.count()) {
                allPlaced = false;
            }
        }
        if (allPlaced) return null;

        Map<String, Integer> have = TradeInventory.snapshot(client, menu);
        TradeSelector.Kind shortage = TradeSelector.shortage(activeOffer, have);
        if (shortage != null) {
            return "Takas girişleri yetersiz (" + shortage.message() + "): " + String.join(" + ", activeOffer.describeInputs()) + ".";
        }
        return "Sunucu takas seçimine yanıt vermedi (giriş eşyaları takas slotlarına taşınmadı).";
    }

    /** Menüdeki oyuncu envanteri slotları arasında sonuç için boş ya da yığına uygun slotu bulur. */
    private int findPlaceSlot(MerchantMenu menu) {
        int start = Math.max(0, menu.slots.size() - 36);
        int firstEmpty = -1;
        int resultCount = activeOffer.output().count();

        for (int i = start; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();

            if (stack.isEmpty()) {
                if (firstEmpty < 0) firstEmpty = i;
            } else if (ItemStack.isSameItemSameComponents(stack, resultStack)
                    && stack.getCount() + resultCount <= stack.getMaxStackSize()) {
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
        // Tüm ayrıntılar konsol günlüğünde kalsın; oyun sohbetinde yalnızca
        // oyuncunun müdahale etmesi gereken veya döngünün bittiğini bildiren mesajları göster.
        ErrorHandler.info(message);
        boolean important = message.startsWith("HATA:")
                || message.startsWith("Döngü bitti.")
                || message.startsWith("Envanterde yer yetmediği")
                || message.startsWith("Shop alımı başarısız")
                || message.startsWith("Depolama atlandı:")
                || message.startsWith("Takas doğrulanamadı:")
                || message.startsWith("Takas menüsü kapatılamadı")
                || message.startsWith("Başlangıçta açık")
                || message.startsWith("Devam edilemedi:");
        if (important && client != null && client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6[Döngü]§r " + message), false);
        }
    }
}
