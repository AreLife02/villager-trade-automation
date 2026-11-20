package com.example.farmerautomation.trade;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Köylünün gerçek tekliflerinden, kullanıcının öncelik sırasına göre bir sonraki işlemi seçer.
 * Teklif veya fiyat uydurmaz: yalnızca okunan tekliflerle eşleşen kuralları uygular.
 */
public final class TradeSelector {
    private TradeSelector() { }

    public enum Kind {
        NONE_ENABLED("bu meslek için etkin takas kuralı yok"),
        NO_OFFER("seçili takaslar köylüde bulunmuyor"),
        OUT_OF_STOCK("seçili takasların stoğu bitmiş"),
        MISSING_EMERALD("zümrüt yetersiz"),
        MISSING_INPUT("takas için gereken eşya yetersiz"),
        MISSING_SECOND_INPUT("takas için gereken ikinci eşya eksik"),
        RULE_LIMIT("seçili takasların en fazla takas sınırı doldu");

        private final String message;

        Kind(String message) { this.message = message; }

        public String message() { return message; }
    }

    public sealed interface Decision permits Trade, Refill, Done { }

    /** Bu teklifle takas yap. */
    public record Trade(OfferInfo offer, TradeRule rule) implements Decision { }

    /** Yüksek öncelikli kuralın ana girişi yetersiz ve kural yenilenebilir (Shop havuç alımı). */
    public record Refill(OfferInfo offer, TradeRule rule) implements Decision { }

    /** Bu köylüde yapılacak takas kalmadı. */
    public record Done(Kind kind) implements Decision { }

    private static final Kind[] PRECEDENCE = {
            Kind.MISSING_EMERALD, Kind.MISSING_INPUT, Kind.MISSING_SECOND_INPUT,
            Kind.OUT_OF_STOCK, Kind.RULE_LIMIT, Kind.NO_OFFER
    };

    /**
     * @param professionId  köylünün mesleği; yalnızca bu mesleğin kuralları uygulanır
     * @param offers        köylüden okunan teklifler
     * @param rules         tüm kurallar; bu meslek için liste sırası öncelik sırasıdır
     * @param tradesByRule  bu köylüyle kural başına yapılan takas sayısı (kural anahtarı -> sayı)
     * @param blocked       bu ziyarette sonuç vermeyen teklif indeksleri
     * @param available     envanterdeki kullanılabilir adetler (kayıt kimliği -> adet)
     * @param refillable    ana girişi yetersiz kalınca Shop'tan yenilenebilen kurallar
     */
    public static Decision decide(String professionId, List<OfferInfo> offers, List<TradeRule> rules,
                                  Map<String, Integer> tradesByRule, Set<Integer> blocked,
                                  Map<String, Integer> available, Predicate<TradeRule> refillable) {
        Set<Kind> seen = EnumSet.noneOf(Kind.class);
        boolean anyEnabled = false;

        for (TradeRule rule : rules) {
            if (!rule.enabled || rule.profession == null || !rule.profession.equalsIgnoreCase(professionId)) continue;
            anyEnabled = true;

            if (rule.maxTrades > 0 && tradesByRule.getOrDefault(rule.key(), 0) >= rule.maxTrades) {
                seen.add(Kind.RULE_LIMIT);
                continue;
            }
            boolean matchedAny = false;
            for (OfferInfo offer : offers) {
                if (!rule.matches(professionId, offer)) continue;
                matchedAny = true;
                if (offer.outOfStock() || blocked.contains(offer.index())) {
                    seen.add(Kind.OUT_OF_STOCK);
                    continue;
                }
                Kind shortage = shortage(offer, available);
                if (shortage == null) return new Trade(offer, rule);
                if (shortage == Kind.MISSING_INPUT && refillable.test(rule)) return new Refill(offer, rule);
                seen.add(shortage);
            }
            if (!matchedAny) seen.add(Kind.NO_OFFER);
        }

        if (!anyEnabled) return new Done(Kind.NONE_ENABLED);
        for (Kind kind : PRECEDENCE) {
            if (seen.contains(kind)) return new Done(kind);
        }
        return new Done(Kind.NO_OFFER);
    }

    /** Takasın bütün girişleri envanterde yeterliyse null, değilse eksiğin türü. */
    public static Kind shortage(OfferInfo offer, Map<String, Integer> available) {
        String primaryBase = offer.primary().baseId();
        for (Map.Entry<String, Integer> need : offer.needs().entrySet()) {
            if (available.getOrDefault(need.getKey(), 0) >= need.getValue()) continue;
            if (OfferInfo.EMERALD.equals(need.getKey())) return Kind.MISSING_EMERALD;
            boolean isPrimary = offer.direction() == TradeDirection.EARN && need.getKey().equals(primaryBase);
            return isPrimary ? Kind.MISSING_INPUT : Kind.MISSING_SECOND_INPUT;
        }
        return null;
    }
}
