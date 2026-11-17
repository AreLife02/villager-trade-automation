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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Aşama A: menü ve teklifleri okur; satın alma ve takas yapmaz. */
public final class DiagnosticController {
    private static final int SHOP_TIMEOUT = 100;
    private static final int MENU_DELAY = 10;
    private static final int CLOSE_TIMEOUT = 40;
    private static final int TRADE_SCREEN_TIMEOUT = 40;
    private static final int OFFERS_TIMEOUT = 40;

    private enum Step {
        OPEN_SHOP, WAIT_SHOP, CLICK_CATEGORY, WAIT_CATEGORY, CLICK_CARROT, WAIT_CARROT,
        DUMP_BUTTONS, CLOSE_SHOP, WAIT_SHOP_CLOSE, SCAN, NEXT_FARMER, INTERACT,
        WAIT_TRADE_SCREEN, WAIT_OFFERS, CLOSE_TRADE, WAIT_TRADE_CLOSE, DONE
    }

    private Step step = Step.OPEN_SHOP;
    private int waitTicks;
    private int elapsedTicks;
    private final List<Villager> accessibleFarmers = new ArrayList<>();
    private int farmerIndex;

    public void reset() {
        step = Step.OPEN_SHOP;
        waitTicks = 0;
        elapsedTicks = 0;
        accessibleFarmers.clear();
        farmerIndex = 0;
    }

    public boolean tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return step == Step.DONE;
        if (step == Step.DONE) return true;

        try {
            switch (step) {
                case OPEN_SHOP -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        return fail(client, "Başlangıçta başka bir konteyner menüsü açıktı; kapatıldı.");
                    }
                    String command = AutomationConfig.get().shopCommand;
                    if (client.player.connection == null) return fail(client, "Sunucu bağlantısı yok; Shop açılamadı.");
                    client.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
                    elapsedTicks = 0;
                    step = Step.WAIT_SHOP;
                }
                case WAIT_SHOP -> {
                    if (client.screen instanceof AbstractContainerScreen<?>) {
                        step = Step.CLICK_CATEGORY;
                        return false;
                    }
                    if (++elapsedTicks > SHOP_TIMEOUT) return fail(client, "Shop menüsü zamanında açılmadı.");
                }
                case CLICK_CATEGORY -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen))
                        return fail(client, "Shop menüsü beklenmedik şekilde kapandı.");
                    Slot slot = ShopController.findSlotByItem(screen, Items.GOLDEN_CARROT);
                    if (slot == null) return fail(client, "Shop menüsünde Altın Havuç bulunamadı.");
                    chat(client, "Altın Havuç kategorisi açılıyor.");
                    ShopController.clickSlot(screen, slot);
                    waitTicks = MENU_DELAY;
                    step = Step.WAIT_CATEGORY;
                }
                case WAIT_CATEGORY -> {
                    if (--waitTicks <= 0) step = Step.CLICK_CARROT;
                }
                case CLICK_CARROT -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen))
                        return fail(client, "Kategori menüsü beklenmedik şekilde kapandı.");
                    Slot slot = ShopController.findSlotByItem(screen, Items.CARROT);
                    if (slot == null) return fail(client, "Kategori menüsünde Havuç bulunamadı.");
                    chat(client, "Havuç miktar menüsü açılıyor; satın alma yapılmayacak.");
                    ShopController.clickSlot(screen, slot);
                    waitTicks = MENU_DELAY;
                    step = Step.WAIT_CARROT;
                }
                case WAIT_CARROT -> {
                    if (--waitTicks <= 0) step = Step.DUMP_BUTTONS;
                }
                case DUMP_BUTTONS -> {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen))
                        return fail(client, "Miktar menüsü beklenmedik şekilde kapandı.");
                    chat(client, "=== Miktar menüsü adları ve açıklamaları ===");
                    var menu = screen.getMenu();
                    int shopSlots = Math.max(0, menu.slots.size() - 36);
                    int found = 0;
                    for (int i = 0; i < shopSlots; i++) {
                        ItemStack stack = menu.slots.get(i).getItem();
                        if (stack.isEmpty()) continue;
                        found++;
                        StringBuilder line = new StringBuilder("Slot ").append(i).append(": ")
                                .append(stack.getHoverName().getString());
                        var customName = stack.get(DataComponents.CUSTOM_NAME);
                        if (customName != null && !customName.getString().equals(stack.getHoverName().getString()))
                            line.append(" | Özel ad: ").append(customName.getString());
                        var lore = stack.get(DataComponents.LORE);
                        if (lore != null) for (Component text : lore.lines()) line.append(" | ").append(text.getString());
                        chat(client, line.toString());
                    }
                    chat(client, "Dolu düğme: " + found + ". +10 ve Zümrüt Bloğu'na tıklanmadı.");
                    client.player.closeContainer();
                    elapsedTicks = 0;
                    step = Step.WAIT_SHOP_CLOSE;
                }
                case WAIT_SHOP_CLOSE -> {
                    if (client.screen == null) {
                        step = Step.SCAN;
                        return false;
                    }
                    if (++elapsedTicks % 10 == 0) client.player.closeContainer();
                    if (elapsedTicks > CLOSE_TIMEOUT) return fail(client, "Shop menüsü kapatılamadı.");
                }
                case SCAN -> scanFarmers(client);
                case NEXT_FARMER -> {
                    if (farmerIndex >= accessibleFarmers.size()) {
                        chat(client, "Teşhis tamamlandı. Satın alma ve takas yapılmadı.");
                        step = Step.DONE;
                        return true;
                    }
                    step = Step.INTERACT;
                }
                case INTERACT -> {
                    Villager villager = accessibleFarmers.get(farmerIndex);
                    if (!isAccessible(client, villager)) {
                        chat(client, "Çiftçi artık erişilebilir değil; bu teşhis durduruluyor.");
                        return fail(client, "Çiftçi etkileşimden önce menzil/görüş koşulunu kaybetti.");
                    }
                    AutomationManager.INSTANCE.setCurrentVillagerIndex(farmerIndex);
                    chat(client, "Çiftçi " + (farmerIndex + 1) + "/" + accessibleFarmers.size() + " takas menüsü açılıyor.");
                    if (client.gameMode == null) return fail(client, "Oyun modu hazır değil; takas menüsü açılamadı.");
                    client.gameMode.interact(client.player, villager, InteractionHand.MAIN_HAND);
                    elapsedTicks = 0;
                    step = Step.WAIT_TRADE_SCREEN;
                }
                case WAIT_TRADE_SCREEN -> {
                    if (client.screen instanceof MerchantScreen) {
                        elapsedTicks = 0;
                        step = Step.WAIT_OFFERS;
                    } else if (++elapsedTicks > TRADE_SCREEN_TIMEOUT) {
                        return fail(client, "Çiftçi takas menüsü zamanında açılmadı.");
                    }
                }
                case WAIT_OFFERS -> {
                    if (!(client.screen instanceof MerchantScreen merchantScreen))
                        return fail(client, "Takas menüsü teklifler okunmadan kapandı.");
                    MerchantMenu menu = merchantScreen.getMenu();
                    if (!menu.getOffers().isEmpty()) {
                        readOffers(client, menu);
                        client.player.closeContainer();
                        elapsedTicks = 0;
                        step = Step.WAIT_TRADE_CLOSE;
                    } else if (++elapsedTicks > OFFERS_TIMEOUT) {
                        return fail(client, "Takas listesi zamanında gelmedi.");
                    }
                }
                case CLOSE_TRADE -> step = Step.WAIT_TRADE_CLOSE;
                case WAIT_TRADE_CLOSE -> {
                    if (client.screen == null) {
                        farmerIndex++;
                        step = Step.NEXT_FARMER;
                    } else {
                        if (++elapsedTicks % 10 == 0) client.player.closeContainer();
                        if (elapsedTicks > CLOSE_TIMEOUT) return fail(client, "Takas menüsü kapatılamadı.");
                    }
                }
                case DONE -> { return true; }
            }
        } catch (Exception exception) {
            return fail(client, "Teşhis hatası: " + exception.getClass().getSimpleName());
        }
        return step == Step.DONE;
    }

    private void scanFarmers(Minecraft client) {
        accessibleFarmers.clear();
        farmerIndex = 0;
        double radius = Math.max(0, AutomationConfig.get().villagerSearchRadius);
        double interactionRange = client.player.entityInteractionRange();
        List<Villager> farmers = new ArrayList<>();
        int villagers = 0;
        for (Entity entity : client.level.getEntities(client.player, client.player.getBoundingBox().inflate(radius))) {
            if (!(entity instanceof Villager villager) || !villager.isAlive()) continue;
            villagers++;
            if (villager.getVillagerData().profession().is(VillagerProfession.FARMER)) farmers.add(villager);
        }
        farmers.sort(Comparator.comparingDouble(client.player::distanceTo));
        AutomationManager.INSTANCE.setVillagerCount(farmers.size());
        chat(client, "=== Çiftçi taraması: " + villagers + " köylü, " + farmers.size() + " çiftçi ===");
        int rank = 0;
        for (Villager villager : farmers) {
            rank++;
            double distance = client.player.distanceTo(villager);
            String distanceText = String.format(java.util.Locale.ROOT, "%.1f", distance);
            if (distance > interactionRange) {
                chat(client, rank + ". çiftçi, " + distanceText + " blok: MENZİL DIŞI");
            } else if (!hasLineOfSight(client, villager)) {
                chat(client, rank + ". çiftçi, " + distanceText + " blok: GÖRÜŞ ENGELLİ");
            } else {
                accessibleFarmers.add(villager);
                chat(client, rank + ". çiftçi, " + distanceText + " blok: ERİŞİLEBİLİR");
            }
        }
        chat(client, "Erişilebilir çiftçi sayısı: " + accessibleFarmers.size() + "; menüler yalnızca okunacak.");
        step = Step.NEXT_FARMER;
    }

    private static boolean isAccessible(Minecraft client, Villager villager) {
        return villager.isAlive()
                && client.player.distanceTo(villager) <= client.player.entityInteractionRange()
                && hasLineOfSight(client, villager);
    }

    private static boolean hasLineOfSight(Minecraft client, Villager villager) {
        Vec3 eye = client.player.getEyePosition();
        Vec3[] targets = {villager.getEyePosition(), villager.position().add(0, villager.getBbHeight() / 2.0, 0)};
        for (Vec3 target : targets) {
            HitResult hit = client.level.clip(new ClipContext(
                    eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private void readOffers(Minecraft client, MerchantMenu menu) {
        int index = 0;
        boolean foundCarrotTrade = false;
        for (MerchantOffer offer : menu.getOffers()) {
            index++;
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            ItemStack result = offer.getResult();
            boolean target = (costA.is(Items.CARROT) || costB.is(Items.CARROT)) && result.is(Items.EMERALD);
            StringBuilder line = new StringBuilder("Takas ").append(index).append(": ")
                    .append(costA.getCount()).append("x ").append(costA.getHoverName().getString());
            if (!costB.isEmpty()) line.append(" + ").append(costB.getCount()).append("x ").append(costB.getHoverName().getString());
            line.append(" -> ").append(result.getCount()).append("x ").append(result.getHoverName().getString())
                    .append(" | stok: ").append(offer.getUses()).append("/").append(offer.getMaxUses())
                    .append(offer.getUses() >= offer.getMaxUses() ? " (TÜKENMİŞ)" : " (stok var)");
            if (target) {
                line.append(" | HEDEF: havuç -> zümrüt");
                foundCarrotTrade = true;
            }
            chat(client, line.toString());
        }
        if (!foundCarrotTrade) chat(client, "Bu çiftçide havuç -> zümrüt teklifi yok.");
    }

    private boolean fail(Minecraft client, String message) {
        closeIfOpen(client);
        chat(client, "HATA: " + message + " Güvenli şekilde durduruldu.");
        step = Step.DONE;
        AutomationManager.INSTANCE.fail("Döngü teşhisi: " + message);
        return true;
    }

    private static void closeIfOpen(Minecraft client) {
        if (client != null && client.player != null && client.screen instanceof AbstractContainerScreen<?>)
            client.player.closeContainer();
    }

    private static void chat(Minecraft client, String message) {
        ErrorHandler.info(message);
        if (client.player != null)
            client.player.displayClientMessage(Component.literal("§6[Otomasyon]§r " + message), false);
    }
}
