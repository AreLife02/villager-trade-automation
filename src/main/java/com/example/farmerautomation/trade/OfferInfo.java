package com.example.farmerautomation.trade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Köylünün oyun içindeki gerçek teklifinin anlık görüntüsü.
 * Fiyatlar getCostA/getCostB üzerinden okunur, yani indirimler dahildir.
 */
public record OfferInfo(
        int index,
        TradeDirection direction,
        Item item,
        String itemId,
        String itemName,
        int itemCount,
        int emeraldCount,
        Item secondaryItem,
        int secondaryCount,
        boolean outOfStock,
        int uses,
        int maxUses
) {
    /** Teklif zümrüt ticareti değilse (ör. kitap + zümrüt -> büyülü kitap dahil olmayan türler) null döner. */
    public static OfferInfo from(int index, MerchantOffer offer) {
        ItemStack costA = offer.getCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.getResult();
        if (costA.isEmpty() || result.isEmpty()) return null;

        boolean resultEmerald = result.is(Items.EMERALD);
        boolean aEmerald = costA.is(Items.EMERALD);
        boolean bEmerald = !costB.isEmpty() && costB.is(Items.EMERALD);

        if (resultEmerald && !aEmerald && !bEmerald && costB.isEmpty()) {
            // eşya -> zümrüt
            return build(index, TradeDirection.EARN, costA, costA.getCount(), result.getCount(),
                    Items.AIR, 0, offer);
        }
        if (!resultEmerald && (aEmerald || bEmerald)) {
            // zümrüt (+ isteğe bağlı ikinci eşya) -> eşya
            ItemStack emeraldStack = aEmerald ? costA : costB;
            ItemStack other = aEmerald ? costB : costA;
            Item secondary = other.isEmpty() ? Items.AIR : other.getItem();
            return build(index, TradeDirection.SPEND, result, result.getCount(), emeraldStack.getCount(),
                    secondary, other.isEmpty() ? 0 : other.getCount(), offer);
        }
        return null;
    }

    private static OfferInfo build(int index, TradeDirection direction, ItemStack itemStack, int itemCount,
                                   int emeraldCount, Item secondary, int secondaryCount, MerchantOffer offer) {
        Item item = itemStack.getItem();
        return new OfferInfo(index, direction, item, BuiltInRegistries.ITEM.getKey(item).toString(),
                itemStack.getHoverName().getString(), itemCount, emeraldCount, secondary, secondaryCount,
                offer.isOutOfStock(), offer.getUses(), offer.getMaxUses());
    }

    public int remainingStock() { return Math.max(0, maxUses - uses); }

    /** Ayar dosyasındaki kural anahtarı: "yön:eşya-kimliği". */
    public String ruleKey() { return direction.name() + ":" + itemId; }
}
