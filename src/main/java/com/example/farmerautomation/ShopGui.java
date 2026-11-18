package com.example.farmerautomation;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Shop GUI'sinde slot bulma ve ShopController ile aynı yöntemle tıklama yardımcıları. */
final class ShopGui {
    private ShopGui() {
    }

    static void clickSlot(AbstractContainerScreen<?> screen, Slot slot) {
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

    static Slot findSlotByItem(AbstractContainerScreen<?> screen, Item item) {
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

    static Slot findSlotByText(AbstractContainerScreen<?> screen, String... texts) {
        var menu = screen.getMenu();
        int shopSlots = Math.max(0, menu.slots.size() - 36);

        for (int index = 0; index < shopSlots; index++) {
            Slot slot = menu.slots.get(index);
            ItemStack stack = slot.getItem();

            if (stack.isEmpty()) continue;

            var name = stack.get(DataComponents.CUSTOM_NAME);

            if (name != null) {
                String nameText = name.getString();
                for (String text : texts) {
                    if (nameText.contains(text)) return slot;
                }
            }

            var lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;

            for (Component line : lore.lines()) {
                String lineText = line.getString();
                for (String text : texts) {
                    if (lineText.contains(text)) return slot;
                }
            }
        }

        return null;
    }
}
