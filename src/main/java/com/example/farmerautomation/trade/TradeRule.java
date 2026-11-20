package com.example.farmerautomation.trade;

/** Kullanıcının bir meslek için seçtiği takas kuralı. Aynı mesleğin kuralları arasında liste sırası = öncelik. */
public final class TradeRule {
    public String profession;
    public String direction;
    public String itemId;
    public boolean enabled = true;
    /** Bu köylüyle bu kuraldan yapılacak en fazla takas; 0 = stok bitene kadar. */
    public int maxTrades = 0;

    public TradeRule() { }

    public TradeRule(String profession, TradeDirection direction, String itemId, boolean enabled) {
        this.profession = profession;
        this.direction = direction.name();
        this.itemId = itemId;
        this.enabled = enabled;
    }

    /**
     * Meslek, yön ve eşya birlikte eşleşmelidir. itemId "#" içermiyorsa aynı eşyanın bütün
     * çeşitleriyle (ör. her büyülü kitap) eşleşir; "#" içeriyorsa tam kimlik gerekir.
     */
    public boolean matches(String professionId, OfferInfo offer) {
        if (offer == null || professionId == null || profession == null || !profession.equalsIgnoreCase(professionId)) {
            return false;
        }
        if (direction == null || !direction.equals(offer.direction().name()) || itemId == null) return false;
        TradeItem primary = offer.primary();
        return itemId.equals(primary.id()) || (!itemId.contains("#") && itemId.equals(primary.baseId()));
    }

    public String key() { return direction + ":" + itemId; }
}
