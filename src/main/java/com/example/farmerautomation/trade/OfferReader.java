package com.example.farmerautomation.trade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Minecraft'ın gerçek MerchantOffer verilerini okur; fiyatlar indirim dahil getCostA/getCostB'den gelir. */
public final class OfferReader {
    private OfferReader() { }

    /** Zümrüt ticareti olmayan teklifler atlanır; indeksler menüdeki teklif sırasını korur. */
    public static List<OfferInfo> read(Iterable<MerchantOffer> offers) {
        List<OfferInfo> result = new ArrayList<>();
        int index = 0;
        for (MerchantOffer offer : offers) {
            List<TradeItem> inputs = new ArrayList<>();
            ItemStack costA = offer.getCostA();
            ItemStack costB = offer.getCostB();
            if (!costA.isEmpty()) inputs.add(item(costA));
            if (!costB.isEmpty()) inputs.add(item(costB));
            ItemStack out = offer.getResult();
            if (!out.isEmpty()) {
                OfferInfo info = OfferInfo.classify(index, inputs, item(out),
                        offer.isOutOfStock(), offer.getUses(), offer.getMaxUses());
                if (info != null) result.add(info);
            }
            index++;
        }
        return result;
    }

    public static String baseId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static TradeItem item(ItemStack stack) {
        String base = baseId(stack);
        String id = base;
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (var entry : stored.entrySet()) {
                parts.add(entry.getKey().getRegisteredName() + "=" + entry.getIntValue());
            }
            Collections.sort(parts);
            id = base + "#" + String.join(",", parts);
        }
        return new TradeItem(id, base, stack.getHoverName().getString(), stack.getCount());
    }
}
