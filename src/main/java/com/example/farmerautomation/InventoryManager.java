package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryManager {
    private static final int SLOT_COUNT = 36;

    private InventoryManager() {
    }

    public static int countCarrots(Minecraft client) {
        if (client == null || client.player == null) return -1;
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.is(Items.CARROT)) count += stack.getCount();
        }
        return count;
    }

    public static int countEmeralds(Minecraft client) {
        if (client == null || client.player == null) return -1;
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.is(Items.EMERALD)) count += stack.getCount();
        }
        return count;
    }

    public static int remainingCarrotCapacity(Minecraft client) {
        if (client == null || client.player == null) return -1;
        ItemStack carrot = new ItemStack(Items.CARROT);
        int capacity = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                capacity += carrot.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(stack, carrot)) {
                capacity += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return capacity;
    }
}
