package com.example.farmerautomation.trade;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Oyuncu envanteri ve takas giriş slotlarındaki eşyaların kayıt kimliğine göre sayımı. */
public final class TradeInventory {
    private TradeInventory() { }

    public static Map<String, Integer> snapshot(Minecraft client, MerchantMenu menu) {
        Map<String, Integer> counts = new HashMap<>();
        if (client == null || client.player == null) return counts;
        for (int i = 0; i < 36; i++) add(counts, client.player.getInventory().getItem(i));
        if (menu != null) {
            for (int slot = 0; slot <= 1; slot++) add(counts, menu.getSlot(slot).getItem());
        }
        return counts;
    }

    private static void add(Map<String, Integer> counts, ItemStack stack) {
        if (stack.isEmpty()) return;
        counts.merge(OfferReader.baseId(stack), stack.getCount(), Integer::sum);
    }
}
