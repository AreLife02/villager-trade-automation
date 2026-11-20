package com.example.farmerautomation.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Köylünün oyun içindeki gerçek teklifinin anlık görüntüsü (Minecraft sınıflarından bağımsız).
 * Girişler teklifteki sırayla tutulur: inputs[0] = takas slotu 0, inputs[1] = takas slotu 1.
 */
public record OfferInfo(
        int index,
        TradeDirection direction,
        List<TradeItem> inputs,
        TradeItem output,
        boolean outOfStock,
        int uses,
        int maxUses
) {
    public static final String EMERALD = "minecraft:emerald";

    /** Zümrüt ticareti olmayan veya anlaşılamayan teklifler için null döner. */
    public static OfferInfo classify(int index, List<TradeItem> inputs, TradeItem output,
                                     boolean outOfStock, int uses, int maxUses) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 2 || output == null || output.count() <= 0) {
            return null;
        }
        boolean anyEmeraldInput = false;
        for (TradeItem input : inputs) {
            if (input.count() <= 0) return null;
            if (EMERALD.equals(input.baseId())) anyEmeraldInput = true;
        }
        TradeDirection direction;
        if (EMERALD.equals(output.baseId())) {
            if (anyEmeraldInput) return null;
            direction = TradeDirection.EARN;
        } else if (anyEmeraldInput) {
            direction = TradeDirection.SPEND;
        } else {
            return null;
        }
        return new OfferInfo(index, direction, List.copyOf(inputs), output, outOfStock, uses, maxUses);
    }

    /** EARN: verilen ana eşya (ilk zümrüt dışı giriş). SPEND: alınan ürün. */
    public TradeItem primary() {
        if (direction == TradeDirection.SPEND) return output;
        for (TradeItem input : inputs) {
            if (!EMERALD.equals(input.baseId())) return input;
        }
        return inputs.get(0);
    }

    /** Ana eşya dışında gereken zümrüt olmayan ek giriş (ör. zümrüt + kitap -> büyülü kitap'taki kitap). */
    public TradeItem secondary() {
        boolean skippedPrimary = direction == TradeDirection.SPEND;
        TradeItem primary = primary();
        for (TradeItem input : inputs) {
            if (EMERALD.equals(input.baseId())) continue;
            if (!skippedPrimary && input == primary) {
                skippedPrimary = true;
                continue;
            }
            return input;
        }
        return null;
    }

    /** Takas için gereken toplam adet (kayıt kimliğine göre; aynı eşya iki girişte olabilir). */
    public Map<String, Integer> needs() {
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (TradeItem input : inputs) needs.merge(input.baseId(), input.count(), Integer::sum);
        return needs;
    }

    /** Takas sonucunda envanterde beklenen net değişim (kayıt kimliği -> artış/azalış). */
    public Map<String, Integer> expectedDelta() {
        Map<String, Integer> delta = new LinkedHashMap<>();
        for (TradeItem input : inputs) delta.merge(input.baseId(), -input.count(), Integer::sum);
        delta.merge(output.baseId(), output.count(), Integer::sum);
        return delta;
    }

    public int remainingStock() { return Math.max(0, maxUses - uses); }

    public List<String> describeInputs() {
        List<String> parts = new ArrayList<>();
        for (TradeItem input : inputs) parts.add(input.count() + " " + input.name());
        return parts;
    }
}
