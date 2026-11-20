package com.example.farmerautomation.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Kullanıcının meslek önceliğinden bu çalışmada gezilecek meslekleri çıkarır. */
public final class TradePlan {
    private TradePlan() { }

    /**
     * @param order  kullanıcının sıralı meslek listesi (ilk = en yüksek öncelik)
     * @param known  geçerli meslek kimliği mi
     * @return sırası korunmuş, tekrarsız, bilinen ve en az bir etkin kuralı olan meslekler
     */
    public static List<String> activeProfessions(List<String> order, List<TradeRule> rules, Predicate<String> known) {
        List<String> result = new ArrayList<>();
        if (order == null) return result;
        for (String id : order) {
            if (id == null || !known.test(id)) continue;
            String normalized = id.toLowerCase(java.util.Locale.ROOT);
            if (result.contains(normalized)) continue;
            boolean hasRule = false;
            for (TradeRule rule : rules) {
                if (rule.enabled && rule.profession != null && rule.profession.equalsIgnoreCase(normalized)) {
                    hasRule = true;
                    break;
                }
            }
            if (hasRule) result.add(normalized);
        }
        return result;
    }
}
