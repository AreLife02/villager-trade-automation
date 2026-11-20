package com.example.farmerautomation.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Köylünün gerçek tekliflerinden, kullanıcının öncelik sırasına göre bir sonraki takası seçer.
 * Fiyat veya teklif uydurmaz: yalnızca oyundan okunan tekliflerle eşleşen kuralları uygular.
 */
public final class TradeSelector {
    private TradeSelector() { }

    public record Choice(OfferInfo offer, TradeRule rule) { }

    /**
     * @param offers        köylünün okunan teklifleri
     * @param rules         bu mesleğin kuralları, öncelik sırasıyla
     * @param tradesByRule  bu köylüyle kural başına yapılan takas sayısı (ruleKey -> sayı)
     * @param emeraldsHave  envanterdeki kullanılabilir zümrüt
     * @param itemAvailable eşya kimliği -> kullanılabilir adet (envanter yönetimi kurallarından sonra)
     * @return seçilen takas ya da null (bu köylüde yapılacak takas kalmadı)
     */
    public static Choice next(List<OfferInfo> offers, List<TradeRule> rules,
                              Map<String, Integer> tradesByRule, int emeraldsHave,
                              Map<String, Integer> itemAvailable) {
        for (TradeRule rule : rules) {
            if (!rule.enabled) continue;
            int done = tradesByRule.getOrDefault(key(rule), 0);
            if (rule.maxTrades > 0 && done >= rule.maxTrades) continue;
            for (OfferInfo offer : offers) {
                if (!rule.matches(offer) || offer.outOfStock()) continue;
                if (canAfford(offer, emeraldsHave, itemAvailable)) return new Choice(offer, rule);
            }
        }
        return null;
    }

    public static boolean canAfford(OfferInfo offer, int emeraldsHave, Map<String, Integer> itemAvailable) {
        if (offer.direction() == TradeDirection.EARN) {
            return itemAvailable.getOrDefault(offer.itemId(), 0) >= offer.itemCount();
        }
        return emeraldsHave >= offer.emeraldCount();
    }

    public static String key(TradeRule rule) {
        return rule.direction + ":" + rule.itemId;
    }

    /** Teklif listesini okur; zümrüt ticareti olmayanlar atlanır. */
    public static List<OfferInfo> read(Iterable<net.minecraft.world.item.trading.MerchantOffer> offers) {
        List<OfferInfo> result = new ArrayList<>();
        int index = 0;
        for (net.minecraft.world.item.trading.MerchantOffer offer : offers) {
            OfferInfo info = OfferInfo.from(index, offer);
            if (info != null) result.add(info);
            index++;
        }
        return result;
    }
}
