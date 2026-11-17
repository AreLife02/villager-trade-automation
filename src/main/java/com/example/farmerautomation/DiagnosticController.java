package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AŞAMA A: Yalnızca teşhis. Satın alma ve takas YOK.
 *
 * Shop tarafında: Altın Havuç ve Havuç'a tıklanır, miktar menüsündeki tüm
 * düğmelerin ad/lore metinleri sohbete yazılır. +10 ve Zümrüt Bloğu'na
 * tıklanmaz (satın alma yapılmaz) ve menü kapatılır.
 *
 * Köylü tarafında: menzil içindeki köylülerden yalnızca çiftçiler listelenir;
 * etkileşim menzili + duvar/cam kesmeyen görüş ışını kontrolünden geçenler
 * "erişilebilir" sayılır. Erişilebilir her çiftçinin takas menüsü açılır,
 * takaslar okunur (maliyet, stok), ancak takas seçilmez ve sonuç slotuna
 * dokunulmaz.
 */
public final class DiagnosticController {
    private static final int SHOP_OPEN_TIMEOUT_TICKS = 100;
    private static final int MENU_WAIT_TICKS = 10;
    private static final int CLOSE_TIMEOUT_TICKS = 40;
    private static final int TRADE_SCREEN_TIMEOUT_TICKS = 40;
    private static final int OFFERS_TIMEOUT_TICKS = 40;

    private enum Step {
        OPEN_SHOP,
        WAIT_SHOP,
        CLICK_CATEGORY,
        WAIT_CATEGORY,
        CLICK_CARROT,
        WAIT_CARROT,
        DUMP_BUTTONS,
        CLOSE_SHOP,
        WAIT_SHOP_CLOSE,
        SCAN,
        NEXT_FARMER,
        INTERACT,
        WAIT_TRADE_SCREEN,
        WAIT_OFFERS,
        READ_TRADES,
        CLOSE_TRADE,
        WAIT_TRADE_CLOSE,
        DONE
    }

    private Step step = Step.OPEN_SHOP;
    private int waitTicks = 0;
    private int elapsedTicks = 0;
    private final List<Villager> accessibleFarmers = new ArrayList<>();
    private int farmerIndex = 0;

    public void reset() {
        step = Step.OPEN_SHOP;
        waitTicks = 0;
        elapsedTicks = 0;
        accessibleFarmers.clear();
        farmerIndex = 0;
    }

    /**
     * true döner: teşhis bitti (başarı) veya hata verildi (fail çağrıldı).
     */
    public boolean tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return step == Step.DONE;
        }

        if (step == Step.DONE) {
            return true;
        }

        try {
            switch (step) {
                case OPEN_SHOP -> {
                    String command = AutomationConfig.get().shopCommand;

                    if (client.player.connection != null) {
                        client.player.connection.sendCommand(
                                command.startsWith("/") ? command.substring(1) : command);
                    }

                    elapsedTicks = 0;
                    step = Step.WAIT_SHOP;
                }

                case WAIT_SHOP -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        step = Step.CLICK_CATEGORY;
                        return false;
                    }

                    elapsedTicks++;

                    if (elapsedTicks > SHOP_OPEN_TIMEOUT_TICKS) {
                        return fail(client, "Shop menüsü açılmadı.");
                    }
                }

                case CLICK_CATEGORY -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
                        return fail(client, "Shop menüsü beklenmedik şekilde kapandı.");
                    }

                    Slot goldenCarrotSlot = ShopController.findSlotByItem(screen, Items.GOLDEN_CARROT);

                    if (goldenCarrotSlot == null) {
                        return fail(client, "Shop menüsünde Altın Havuç bulunamadı.");
                    }

                    chat(client, "Altın Havuç'a tıklandı.");
                    ShopController.clickSlot(screen, goldenCarrotSlot);
                    waitTicks = MENU_WAIT_TICKS;
                    step = Step.WAIT_CATEGORY;
                }

                case WAIT_CATEGORY -> {
                    waitTicks--;

                    if (waitTicks <= 0) {
                        step = Step.CLICK_CARROT;
                    }
                }

                case CLICK_CARROT -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
                        return fail(client, "Kategori menüsü beklenmedik şekilde kapandı.");
                    }

                    Slot carrotSlot = ShopController.findSlotByItem(screen, Items.CARROT);

                    if (carrotSlot == null) {
                        return fail(client, "Kategori menüsünde havuç bulunamadı.");
                    }

                    chat(client, "Havuç'a tıklandı. Miktar menüsü düğmeleri okunuyor...");
                    ShopController.clickSlot(screen, carrotSlot);
                    waitTicks = MENU_WAIT_TICKS;
                    step = Step.WAIT_CARROT;
                }

                case WAIT_CARROT -> {
                    waitTicks--;

                    if (waitTicks <= 0) {
                        step = Step.DUMP_BUTTONS;
                    }
                }

                case DUMP_BUTTONS -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
                        return fail(client, "Miktar menüsü beklenmedik şekilde kapandı.");
                    }

                    chat(client, "=== Miktar menüsü düğmeleri (satın alma YAPILMADI) ===");

                    var menu = screen.getMenu();
                    int shopSlots = Math.max(0, menu.slots.size() - 36);
                    int nonEmpty = 0;

                    for (int index = 0; index < shopSlots; index++) {
                        ItemStack stack = menu.slots.get(index).getItem();

                        if (stack.isEmpty()) {
                            continue;
                        }

                        nonEmpty++;

                        StringBuilder line = new StringBuilder();
                        line.append("Slot ").append(index).append(": ");
                        line.append(stack.getHoverName().getString());

                        var customName = stack.get(DataComponents.CUSTOM_NAME);

                        if (customName != null
                                && !customName.getString().equals(stack.getHoverName().getString())) {
                            line.append(" | Özel ad: ").append(customName.getString());
                        }

                        var lore = stack.get(DataComponents.LORE);

                        if (lore != null) {
                            for (Component loreLine : lore.lines()) {
                                line.append(" | ").append(loreLine.getString());
                            }
                        }

                        chat(client, line.toString());
                    }

                    chat(client, "Toplam " + shopSlots + " shop slotu tarandı, "
                            + nonEmpty + " tanesi dolu. +10 ve Zümrüt Bloğu'na tıklanmadı.");
                    client.player.closeContainer();
                    elapsedTicks = 0;
                    step = Step.WAIT_SHOP_CLOSE;
                }

                case WAIT_SHOP_CLOSE -> {
                    if (client.screen == null) {
                        step = Step.SCAN;
                        return false;
                    }

                    elapsedTicks++;

                    if (elapsedTicks % 10 == 0) {
                        client.player.closeContainer();
                    }

                    if (elapsedTicks > CLOSE_TIMEOUT_TICKS) {
                        return fail(client, "Shop menüsü kapatılamadı.");
                    }
                }

                case SCAN -> {
                    accessibleFarmers.clear();
                    farmerIndex = 0;

                    double radius = AutomationConfig.get().villagerSearchRadius;
                    double interactionRange = client.player.entityInteractionRange();

                    List<Villager> farmers = new ArrayList<>();
                    int villagerTotal = 0;

                    for (Entity entity : client.level.getEntities(
                            client.player, client.player.getBoundingBox().inflate(radius))) {
                        if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                            continue;
                        }

                        villagerTotal++;

                        // 1.21.11'de meslekler kayıt tabanlı: profession() Holder döner.
                        if (villager.getVillagerData().profession().is(VillagerProfession.FARMER)) {
                            farmers.add(villager);
                        }
                    }

                    farmers.sort(Comparator.comparingDouble(client.player::distanceTo));
                    AutomationManager.INSTANCE.setVillagerCount(farmers.size());

                    chat(client, "=== Köylü taraması ===");
                    chat(client, "Menzil içinde " + villagerTotal + " köylü bulundu, "
                            + farmers.size() + " tanesi çiftçi.");

                    int rank = 0;

                    for (Villager villager : farmers) {
                        rank++;
                        double distance = client.player.distanceTo(villager);
                        String distanceText = String.format("%.1f", distance);

                        if (distance > interactionRange) {
                            chat(client, rank + ". çiftçi - " + distanceText + " blok - UZAK "
                                    + "(etkileşim menzili " + String.format("%.1f", interactionRange) + ")");
                            continue;
                        }

                        if (!hasLineOfSight(client, villager)) {
                            chat(client, rank + ". çiftçi - " + distanceText
                                    + " blok - GÖRÜŞ KAPALI (duvar/cam engeli)");
                            continue;
                        }

                        accessibleFarmers.add(villager);
                        chat(client, rank + ". çiftçi - " + distanceText + " blok - ERİŞİLEBİLİR");
                    }

                    chat(client, "Erişilebilir çiftçi: " + accessibleFarmers.size()
                            + ". Takas menüleri okunuyor (takas seçilmiyor, sonuç slotuna dokunulmuyor)...");
                    step = Step.NEXT_FARMER;
                }

                case NEXT_FARMER -> {
                    if (farmerIndex >= accessibleFarmers.size()) {
                        chat(client, "=== Teşhis tamamlandı ===");
                        chat(client, "Erişilebilir çiftçi: " + accessibleFarmers.size()
                                + ". Satın alma ve takas yapılmadı (Aşama A).");
                        step = Step.DONE;
                        return true;
                    }

                    step = Step.INTERACT;
                }

                case INTERACT -> {
                    Villager villager = accessibleFarmers.get(farmerIndex);

                    if (!villager.isAlive() || client.player.distanceTo(villager)
                            > client.player.entityInteractionRange()) {
                        chat(client, "Çiftçi artık erişilebilir değil, sıradakine geçiliyor.");
                        farmerIndex++;
                        step = Step.NEXT_FARMER;
                        return false;
                    }

                    chat(client, "--- " + (farmerIndex + 1) + "/" + accessibleFarmers.size()
                            + " çiftçi takas menüsü açılıyor ---");

                    if (client.gameMode != null) {
                        client.gameMode.interact(client.player, villager, InteractionHand.MAIN_HAND);
                    }

                    elapsedTicks = 0;
                    step = Step.WAIT_TRADE_SCREEN;
                }

                case WAIT_TRADE_SCREEN -> {
                    if (client.screen instanceof MerchantScreen) {
                        elapsedTicks = 0;
                        step = Step.WAIT_OFFERS;
                        return false;
                    }

                    elapsedTicks++;

                    if (elapsedTicks > TRADE_SCREEN_TIMEOUT_TICKS) {
                        chat(client, "Takas menüsü açılmadı, sıradaki çiftçiye geçiliyor.");
                        closeIfOpen(client);
                        farmerIndex++;
                        step = Step.NEXT_FARMER;
                    }
                }

                case WAIT_OFFERS -> {
                    if (!(client.screen instanceof MerchantScreen merchantScreen)) {
                        chat(client, "Takas menüsü beklenmedik şekilde kapandı.");
                        farmerIndex++;
                        step = Step.NEXT_FARMER;
                        return false;
                    }

                    MerchantMenu menu = (MerchantMenu) merchantScreen.getMenu();

                    if (!menu.getOffers().isEmpty()) {
                        readOffers(client, menu);
                        client.player.closeContainer();
                        elapsedTicks = 0;
                        step = Step.WAIT_TRADE_CLOSE;
                        return false;
                    }

                    elapsedTicks++;

                    if (elapsedTicks > OFFERS_TIMEOUT_TICKS) {
                        chat(client, "Takas listesi okunamadı (sunucu göndermedi), sıradaki çiftçiye geçiliyor.");
                        closeIfOpen(client);
                        farmerIndex++;
                        step = Step.NEXT_FARMER;
                    }
                }

                case READ_TRADES -> {
                    // Okuma WAIT_OFFERS içinde yapılır.
                    step = Step.CLOSE_TRADE;
                }

                case WAIT_TRADE_CLOSE -> {
                    if (client.screen == null) {
                        farmerIndex++;
                        step = Step.NEXT_FARMER;
                        return false;
                    }

                    elapsedTicks++;

                    if (elapsedTicks % 10 == 0) {
                        client.player.closeContainer();
                    }

                    if (elapsedTicks > CLOSE_TIMEOUT_TICKS) {
                        return fail(client, "Takas menüsü kapatılamadı.");
                    }
                }

                default -> {
                    // CLOSE_SHOP, DONE vb. başka yerlerde işlenir.
                }
            }
        } catch (Exception exception) {
            closeIfOpen(client);
            AutomationManager.INSTANCE.fail("Döngü teşhisi beklenmeyen hata: "
                    + exception.getClass().getSimpleName());
            step = Step.DONE;
            return true;
        }

        return step == Step.DONE;
    }

    private void readOffers(Minecraft client, MerchantMenu menu) {
        MerchantOffers offers = menu.getOffers();
        int index = 0;
        boolean foundTarget = false;

        for (MerchantOffer offer : offers) {
            index++;

            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult();

            StringBuilder line = new StringBuilder();
            line.append("Takas ").append(index).append(": ");
            line.append(costA.getCount()).append("x ").append(costA.getHoverName().getString());

            if (!costB.isEmpty()) {
                line.append(" + ").append(costB.getCount())
                        .append("x ").append(costB.getHoverName().getString());
            }

            line.append(" -> ").append(result.getCount())
                    .append("x ").append(result.getHoverName().getString());
            line.append(" | stok: ").append(offer.getUses())
                    .append("/").append(offer.getMaxUses());

            boolean isTarget = (costA.is(Items.CARROT) || costB.is(Items.CARROT))
                    && result.is(Items.EMERALD);

            if (isTarget) {
                line.append(" | HEDEF TAKAS (havuç -> zümrüt)");
                foundTarget = true;
            }

            chat(client, line.toString());
        }

        if (!foundTarget) {
            chat(client, "UYARI: Bu çiftçide havuç -> zümrüt takası bulunamadı.");
        }
    }

    /**
     * Oyuncunun gözünden köylünün gözüne ve gövde merkezine ışın atılır.
     * İkisi de bir bloğa (duvar/cam dahil) çarpmıyorsa görüş açıktır.
     */
    private static boolean hasLineOfSight(Minecraft client, Villager villager) {
        Vec3 eye = client.player.getEyePosition();
        Vec3[] targets = {
                villager.getEyePosition(),
                villager.position().add(0.0, villager.getBbHeight() / 2.0, 0.0)
        };

        for (Vec3 target : targets) {
            HitResult hit = client.level.clip(new ClipContext(
                    eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));

            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }

        return false;
    }

    private boolean fail(Minecraft client, String message) {
        closeIfOpen(client);
        chat(client, "HATA: " + message);
        AutomationManager.INSTANCE.fail("Döngü teşhisi: " + message);
        step = Step.DONE;
        return true;
    }

    private static void closeIfOpen(Minecraft client) {
        if (client.player != null && client.screen instanceof AbstractContainerScreen) {
            client.player.closeContainer();
        }
    }

    private static void chat(Minecraft client, String message) {
        ErrorHandler.info(message);

        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("§6[Otomasyon]§r " + message), false);
        }
    }
}
