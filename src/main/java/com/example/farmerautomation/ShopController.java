package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class ShopController {
    private static final int TARGET_CARROTS = 768;
    private static final int CATEGORY_WAIT_TICKS = 3;
    private static final int CARROT_WAIT_TICKS = 3;
    private static final int PLUS_TEN_WAIT_TICKS = 2;
    private static final int PURCHASE_WAIT_TICKS = 7;
    private static final int MAX_PURCHASES = 12;

    private enum Step {
        OPEN_CATEGORY,
        WAIT_CATEGORY_MENU,
        BUY_CARROTS,
        WAIT_CARROT_MENU,
        SELECT_64,
        CONFIRM_PURCHASE,
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
                Slot goldenCarrotSlot = findSlotByItem(screen, Items.GOLDEN_CARROT);

                if (goldenCarrotSlot == null) {
                    AutomationManager.INSTANCE.fail("Shop menüsünde Altın Havuç bulunamadı.");
                    return false;
                }

                clickSlot(screen, goldenCarrotSlot);
                waitTicks = CATEGORY_WAIT_TICKS;
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

                Slot carrotSlot = findSlotByItem(screen, Items.CARROT);

                if (carrotSlot == null) {
                    AutomationManager.INSTANCE.fail("Kategori menüsünde havuç bulunamadı.");
                    return false;
                }

                carrotsBeforePurchase = InventoryManager.countCarrots(client);
                clickSlot(screen, carrotSlot);

                waitTicks = CARROT_WAIT_TICKS;
                step = Step.SELECT_64;
            }

            case SELECT_64 -> {
                if (waitTicks > 0) {
                    waitTicks--;
                    return false;
                }

                Slot plusTenSlot = findSlotByText(screen, "+10", "10 set arttır", "+160");

                if (plusTenSlot == null) {
                    AutomationManager.INSTANCE.fail("64 havuç seçimi için +10 düğmesi bulunamadı.");
                    return false;
                }

                clickSlot(screen, plusTenSlot);
                waitTicks = PLUS_TEN_WAIT_TICKS;
                step = Step.CONFIRM_PURCHASE;
            }

            case CONFIRM_PURCHASE -> {
                if (waitTicks > 0) {
                    waitTicks--;
                    return false;
                }

                Slot emeraldBlockSlot = findSlotByItem(screen, Items.EMERALD_BLOCK);

                if (emeraldBlockSlot == null) {
                    AutomationManager.INSTANCE.fail("Satın alma onayı için zümrüt bloğu bulunamadı.");
                    return false;
                }

                clickSlot(screen, emeraldBlockSlot);

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
                    AutomationManager.INSTANCE.fail("Satın alma sonrası envanterdeki havuç sayısı artmadı.");
                    return false;
                }

                step = Step.BUY_CARROTS;
            }
        }

        return false;
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, Slot slot) {
        int containerRows = Math.max(0, (screen.getMenu().slots.size() - 36) / 9);
        int imageWidth = 176;
        int imageHeight = 114 + containerRows * 18;

        double leftPos = (screen.width - imageWidth) / 2.0;
        double topPos = (screen.height - imageHeight) / 2.0;

        double guiX = leftPos + slot.x + 8;
        double guiY = topPos + slot.y + 8;

        MouseButtonInfo left = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
        MouseButtonEvent event = new MouseButtonEvent(guiX, guiY, left);

        screen.mouseMoved(guiX, guiY);
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static Slot findSlotByItem(AbstractContainerScreen<?> screen, net.minecraft.world.item.Item item) {
        var menu = screen.getMenu();

        int shopSlots = Math.max(0, menu.slots.size() - 36);

        for (int index = 0; index < shopSlots; index++) {
            Slot slot = menu.slots.get(index);
            ItemStack stack = slot.getItem();

            if (!stack.isEmpty() && stack.is(item)) {
                return slot;
            }
        }

        return null;
    }

    private static Slot findSlotByText(AbstractContainerScreen<?> screen, String... texts) {
        var menu = screen.getMenu();

        int shopSlots = Math.max(0, menu.slots.size() - 36);

        for (int index = 0; index < shopSlots; index++) {
            Slot slot = menu.slots.get(index);
            ItemStack stack = slot.getItem();

            if (stack.isEmpty()) {
                continue;
            }

            var name = stack.get(DataComponents.CUSTOM_NAME);

            if (name != null) {
                String nameText = name.getString();

                for (String text : texts) {
                    if (nameText.contains(text)) {
                        return slot;
                    }
                }
            }

            var lore = stack.get(DataComponents.LORE);

            if (lore == null) {
                continue;
            }

            for (Component line : lore.lines()) {
                String lineText = line.getString();

                for (String text : texts) {
                    if (lineText.contains(text)) {
                        return slot;
                    }
                }
            }
        }

        return null;
    }

    public void reset() {
        step = Step.OPEN_CATEGORY;
        waitTicks = 0;
        purchases = 0;
        carrotsBeforePurchase = -1;
    }
}
