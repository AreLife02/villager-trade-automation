package com.example.farmerautomation.trade;

/** Kullanıcının bir meslek için seçtiği takas kuralı. Liste sırası = öncelik. */
public final class TradeRule {
    public String profession;
    public String direction;
    public String itemId;
    public boolean enabled = true;
    /** Bu köylüyle bu kuraldan yapılacak en fazla takas; 0 = stok bitene kadar. */
    public int maxTrades = 0;

    public TradeRule() { }

    public TradeRule(TradeProfession profession, TradeDirection direction, String itemId) {
        this.profession = profession.id();
        this.direction = direction.name();
        this.itemId = itemId;
    }

    public boolean matches(OfferInfo offer) {
        return offer != null && direction != null && direction.equals(offer.direction().name())
                && itemId != null && itemId.equals(offer.itemId());
    }
}
