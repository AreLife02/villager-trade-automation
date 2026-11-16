package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShopController {
    private static final int TARGET_CARROTS = 768;
    private static final int VERIFICATION_DELAY_TICKS = 20;
    private static final int MAX_CLICKS = 40;

    private int lastCarrotCount = -1;
    private int clicks = 0;
    private int verificationTicks = 0;
    private int lastClickedSlot = -1;

    public boolean isFinished(Minecraft client) {
        int carrots = InventoryManager.countCarrots(client);
        return carrots >= TARGET_CARROTS;
    }

    public boolean tick(Minecraft client) {
        if (client == null || client.player == null) {
            return false;
        }

        if (isFinished(client)) {
            return true;
        }

        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }

        if (verificationTicks > 0) {
            verificationTicks--;

            if (verificationTicks == 0) {
                int current = InventoryManager.countCarrots(client);

                if (current <= lastCarrotCount) {
                    ErrorHandler.error("Shop tıklaması envanterdeki havuç sayısını artırmadı. Otomasyon durduruldu.");
                    AutomationManager.INSTANCE.fail("Shop alımı doğrulanamadı.");
                    return false;
                }

                lastCarrotCount = current;
                lastClickedSlot = -1;
            }

            return false;
        }

        if (clicks >= MAX_CLICKS) {
            ErrorHandler.error("Shop alımı için tıklama sınırına ulaşıldı.");
            AutomationManager.INSTANCE.fail("Shop alımı tamamlanamadı.");
            return false;
        }

        int carrotSlot = findCarrotSlot(screen);

        if (carrotSlot < 0) {
            ErrorHandler.error("Shop menüsünde havuç bulunamadı.");
            AutomationManager.INSTANCE.fail("Shop menüsünde havuç bulunamadı.");
            return false;
        }

        if (carrotSlot == lastClickedSlot) {
            ErrorHandler.error("Aynı Shop slotu tekrar tıklanmaya çalışıldı.");
            AutomationManager.INSTANCE.fail("Shop alımı doğrulanamadı.");
            return false;
        }

        int before = InventoryManager.countCarrots(client);
        lastCarrotCount = before;
        lastClickedSlot = carrotSlot;
        clicks++;

        screen.getMenu().clicked(carrotSlot, 0, ClickType.PICKUP, client.player);

        verificationTicks = VERIFICATION_DELAY_TICKS;
        return false;
    }

    private static int findCarrotSlot(AbstractContainerScreen<?> screen) {
        var menu = screen.getMenu();

        for (int index = 0; index < menu.slots.size(); index++) {
            ItemStack stack = menu.slots.get(index).getItem();

            if (!stack.isEmpty() && stack.is(Items.CARROT)) {
                return index;
            }
        }

        return -1;
    }

    public void reset() {
        lastCarrotCount = -1;
        clicks = 0;
        verificationTicks = 0;
        lastClickedSlot = -1;
    }
}
