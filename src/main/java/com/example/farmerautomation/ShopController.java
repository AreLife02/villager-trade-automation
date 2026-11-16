package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShopController {
    private static final int TARGET_CARROTS = 768;
    private static final int MENU_WAIT_TICKS = 20;
    private static final int PURCHASE_WAIT_TICKS = 20;
    private static final int MAX_PURCHASES = 40;

    private enum Step {
        OPEN_CATEGORY,
        WAIT_CATEGORY_MENU,
        BUY_CARROTS,
        WAIT_PURCHASE
    }

    private Step step = Step.OPEN_CATEGORY;
    private int waitTicks = 0;
    private int purchases = 0;
    private int carrotsBeforePurchase = -1;

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

        switch (step) {
            case OPEN_CATEGORY -> {
                int goldenCarrotSlot = findSlot(screen, Items.GOLDEN_CARROT);

                if (goldenCarrotSlot < 0) {
                    AutomationManager.INSTANCE.fail("Shop menüsünde Altın Havuç bulunamadı.");
                    return false;
                }

                clickSlot(screen, goldenCarrotSlot);
                waitTicks = MENU_WAIT_TICKS;
                step = Step.WAIT_CATEGORY_MENU;
            }

            case WAIT_CATEGORY_MENU -> {
                waitTicks--;

                if (waitTicks <= 0) {
                    step = Step.BUY_CARROTS;
                }
            }

            case BUY_CARROTS -> {
                if (purchases >= MAX_PURCHASES) {
                    AutomationManager.INSTANCE.fail("Shop alımı için tıklama sınırına ulaşıldı.");
                    return false;
                }

                int carrotSlot = findSlot(screen, Items.CARROT);

                if (carrotSlot < 0) {
                    AutomationManager.INSTANCE.fail("Kategori menüsünde havuç bulunamadı.");
                    return false;
                }

                carrotsBeforePurchase = InventoryManager.countCarrots(client);
                clickSlot(screen, carrotSlot);

                purchases++;
                waitTicks = PURCHASE_WAIT_TICKS;
                step = Step.WAIT_PURCHASE;
            }

            case WAIT_PURCHASE -> {
                waitTicks--;

                if (waitTicks > 0) {
                    return false;
                }

                int carrotsNow = InventoryManager.countCarrots(client);

                if (carrotsNow <= carrotsBeforePurchase) {
                    AutomationManager.INSTANCE.fail("Havuç tıklaması envanterdeki havuç sayısını artırmadı.");
                    return false;
                }

                step = Step.BUY_CARROTS;
            }
        }

        return false;
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, int slot) {
        screen.getMenu().clicked(slot, 0, ClickType.PICKUP, Minecraft.getInstance().player);
    }

    private static int findSlot(AbstractContainerScreen<?> screen, net.minecraft.world.item.Item item) {
        var menu = screen.getMenu();

        for (int index = 0; index < menu.slots.size(); index++) {
            ItemStack stack = menu.slots.get(index).getItem();

            if (!stack.isEmpty() && stack.is(item)) {
                return index;
            }
        }

        return -1;
    }

    public void reset() {
        step = Step.OPEN_CATEGORY;
        waitTicks = 0;
        purchases = 0;
        carrotsBeforePurchase = -1;
    }
}
