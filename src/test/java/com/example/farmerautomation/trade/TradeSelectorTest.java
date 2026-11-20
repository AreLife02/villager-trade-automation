package com.example.farmerautomation.trade;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeSelectorTest {
    private static TradeItem item(String name, int count) {
        return new TradeItem("minecraft:" + name, "minecraft:" + name, name, count);
    }

    private static OfferInfo earn(int index, String give, int giveCount, int emeralds, boolean oos) {
        return OfferInfo.classify(index, List.of(item(give, giveCount)), item("emerald", emeralds), oos, 0, 16);
    }

    private static OfferInfo spend(int index, int emeralds, String get, int getCount) {
        return OfferInfo.classify(index, List.of(item("emerald", emeralds)), item(get, getCount), false, 0, 12);
    }

    private static TradeRule rule(String profession, TradeDirection d, String item) {
        return new TradeRule(profession, d, "minecraft:" + item, true);
    }

    private static Map<String, Integer> inv(Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put("minecraft:" + kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private static TradeSelector.Decision decide(String prof, List<OfferInfo> offers, List<TradeRule> rules,
                                                 Map<String, Integer> inventory) {
        return TradeSelector.decide(prof, offers, rules, new HashMap<>(), new HashSet<>(), inventory, r -> false);
    }

    @Test
    void farmerCarrotToEmerald() {
        var offers = List.of(earn(0, "carrot", 22, 1, false));
        var d = decide("farmer", offers, List.of(rule("farmer", TradeDirection.EARN, "carrot")), inv("carrot", 64));
        assertInstanceOf(TradeSelector.Trade.class, d);
        assertEquals(0, ((TradeSelector.Trade) d).offer().index());
    }

    @Test
    void farmerWheatToEmerald() {
        var offers = List.of(earn(0, "carrot", 22, 1, false), earn(1, "wheat", 20, 1, false));
        var d = decide("farmer", offers, List.of(rule("farmer", TradeDirection.EARN, "wheat")), inv("wheat", 40));
        assertEquals(1, ((TradeSelector.Trade) d).offer().index());
    }

    @Test
    void librarianSpendEmeraldsForProduct() {
        var offers = List.of(earn(0, "paper", 24, 1, false), spend(1, 1, "glass", 4));
        var d = decide("librarian", offers, List.of(rule("librarian", TradeDirection.SPEND, "glass")), inv("emerald", 5));
        var trade = (TradeSelector.Trade) d;
        assertEquals(1, trade.offer().index());
        assertEquals(TradeDirection.SPEND, trade.offer().direction());
    }

    @Test
    void secondInputRequired() {
        // zümrüt + kitap -> ürün
        var offer = OfferInfo.classify(0, List.of(item("emerald", 5), item("book", 1)),
                item("enchanted_book", 1), false, 0, 12);
        assertEquals(TradeDirection.SPEND, offer.direction());
        assertEquals("minecraft:book", offer.secondary().baseId());
        var rules = List.of(rule("librarian", TradeDirection.SPEND, "enchanted_book"));

        var noBook = decide("librarian", List.of(offer), rules, inv("emerald", 10));
        assertEquals(TradeSelector.Kind.MISSING_SECOND_INPUT, ((TradeSelector.Done) noBook).kind());

        var ok = decide("librarian", List.of(offer), rules, inv("emerald", 10, "book", 1));
        assertInstanceOf(TradeSelector.Trade.class, ok);
    }

    @Test
    void earnWithTwoInputsChecksBoth() {
        var offer = OfferInfo.classify(0, List.of(item("paper", 24), item("ink_sac", 2)),
                item("emerald", 1), false, 0, 16);
        assertEquals(TradeDirection.EARN, offer.direction());
        assertEquals("minecraft:paper", offer.primary().baseId());
        assertEquals("minecraft:ink_sac", offer.secondary().baseId());
        var rules = List.of(rule("librarian", TradeDirection.EARN, "paper"));

        var d1 = decide("librarian", List.of(offer), rules, inv("paper", 48));
        assertEquals(TradeSelector.Kind.MISSING_SECOND_INPUT, ((TradeSelector.Done) d1).kind());
        var d2 = decide("librarian", List.of(offer), rules, inv("paper", 48, "ink_sac", 2));
        assertInstanceOf(TradeSelector.Trade.class, d2);
    }

    @Test
    void sameItemInBothInputsIsSummed() {
        var offer = OfferInfo.classify(0, List.of(item("paper", 10), item("paper", 10)),
                item("emerald", 1), false, 0, 16);
        assertEquals(20, offer.needs().get("minecraft:paper"));
        var rules = List.of(rule("librarian", TradeDirection.EARN, "paper"));
        assertInstanceOf(TradeSelector.Done.class, decide("librarian", List.of(offer), rules, inv("paper", 15)));
        assertInstanceOf(TradeSelector.Trade.class, decide("librarian", List.of(offer), rules, inv("paper", 20)));
    }

    @Test
    void insufficientEmerald() {
        var offers = List.of(spend(0, 3, "glass", 4));
        var d = decide("librarian", offers, List.of(rule("librarian", TradeDirection.SPEND, "glass")), inv("emerald", 2));
        assertEquals(TradeSelector.Kind.MISSING_EMERALD, ((TradeSelector.Done) d).kind());
    }

    @Test
    void insufficientInputItem() {
        var offers = List.of(earn(0, "wheat", 20, 1, false));
        var d = decide("farmer", offers, List.of(rule("farmer", TradeDirection.EARN, "wheat")), inv("wheat", 19));
        assertEquals(TradeSelector.Kind.MISSING_INPUT, ((TradeSelector.Done) d).kind());
    }

    @Test
    void outOfStockOfferIsSkipped() {
        var offers = List.of(earn(0, "wheat", 20, 1, true));
        var d = decide("farmer", offers, List.of(rule("farmer", TradeDirection.EARN, "wheat")), inv("wheat", 99));
        assertEquals(TradeSelector.Kind.OUT_OF_STOCK, ((TradeSelector.Done) d).kind());
    }

    @Test
    void blockedOfferIsSkipped() {
        var offers = List.of(earn(0, "wheat", 20, 1, false));
        var d = TradeSelector.decide("farmer", offers, List.of(rule("farmer", TradeDirection.EARN, "wheat")),
                new HashMap<>(), Set.of(0), inv("wheat", 99), r -> false);
        assertEquals(TradeSelector.Kind.OUT_OF_STOCK, ((TradeSelector.Done) d).kind());
    }

    @Test
    void movesToNextRuleOnSameVillagerWhenFirstIsOutOfStock() {
        var offers = List.of(earn(0, "carrot", 22, 1, true), earn(1, "wheat", 20, 1, false));
        var rules = List.of(rule("farmer", TradeDirection.EARN, "carrot"), rule("farmer", TradeDirection.EARN, "wheat"));
        var d = decide("farmer", offers, rules, inv("carrot", 99, "wheat", 99));
        assertEquals(1, ((TradeSelector.Trade) d).offer().index());
    }

    @Test
    void userRuleOrderIsPriorityNotOfferOrder() {
        var offers = List.of(earn(0, "carrot", 22, 1, false), earn(1, "wheat", 20, 1, false));
        var wheatFirst = List.of(rule("farmer", TradeDirection.EARN, "wheat"), rule("farmer", TradeDirection.EARN, "carrot"));
        var d = decide("farmer", offers, wheatFirst, inv("carrot", 99, "wheat", 99));
        assertEquals(1, ((TradeSelector.Trade) d).offer().index());
    }

    @Test
    void ruleOfAnotherProfessionNeverMatches() {
        var offers = List.of(earn(0, "wheat", 20, 1, false));
        var butcherRule = List.of(rule("butcher", TradeDirection.EARN, "wheat"));
        var d = decide("farmer", offers, butcherRule, inv("wheat", 99));
        assertEquals(TradeSelector.Kind.NONE_ENABLED, ((TradeSelector.Done) d).kind());
        assertFalse(butcherRule.get(0).matches("farmer", offers.get(0)));
        assertTrue(butcherRule.get(0).matches("butcher", offers.get(0)));
    }

    @Test
    void directionMustMatch() {
        var spendRule = rule("farmer", TradeDirection.SPEND, "wheat");
        assertFalse(spendRule.matches("farmer", earn(0, "wheat", 20, 1, false)));
    }

    @Test
    void maxTradesPerRuleIsHonored() {
        var offers = List.of(earn(0, "wheat", 20, 1, false), earn(1, "carrot", 22, 1, false));
        var wheat = rule("farmer", TradeDirection.EARN, "wheat");
        wheat.maxTrades = 2;
        var rules = List.of(wheat, rule("farmer", TradeDirection.EARN, "carrot"));
        Map<String, Integer> done = new HashMap<>();
        done.put(wheat.key(), 2);
        var d = TradeSelector.decide("farmer", offers, rules, done, new HashSet<>(),
                inv("wheat", 99, "carrot", 99), r -> false);
        assertEquals(1, ((TradeSelector.Trade) d).offer().index());
    }

    @Test
    void refillRequestedForHigherPriorityRefillableRule() {
        var offers = List.of(earn(0, "carrot", 22, 1, false), earn(1, "wheat", 20, 1, false));
        var rules = List.of(rule("farmer", TradeDirection.EARN, "carrot"), rule("farmer", TradeDirection.EARN, "wheat"));
        var d = TradeSelector.decide("farmer", offers, rules, new HashMap<>(), new HashSet<>(),
                inv("carrot", 5, "wheat", 99), r -> r.itemId.equals("minecraft:carrot"));
        assertInstanceOf(TradeSelector.Refill.class, d);
    }

    @Test
    void noRefillWhenCarrotOfferOutOfStock() {
        var offers = List.of(earn(0, "carrot", 22, 1, true), earn(1, "wheat", 20, 1, false));
        var rules = List.of(rule("farmer", TradeDirection.EARN, "carrot"), rule("farmer", TradeDirection.EARN, "wheat"));
        var d = TradeSelector.decide("farmer", offers, rules, new HashMap<>(), new HashSet<>(),
                inv("carrot", 5, "wheat", 99), r -> r.itemId.equals("minecraft:carrot"));
        assertInstanceOf(TradeSelector.Trade.class, d);
    }

    @Test
    void enchantedBookVariantMatching() {
        var book = new TradeItem("minecraft:enchanted_book#minecraft:mending=1", "minecraft:enchanted_book", "Kitap", 1);
        var offer = OfferInfo.classify(0, List.of(item("emerald", 30), item("book", 1)), book, false, 0, 12);
        var any = new TradeRule("librarian", TradeDirection.SPEND, "minecraft:enchanted_book", true);
        var exact = new TradeRule("librarian", TradeDirection.SPEND, "minecraft:enchanted_book#minecraft:mending=1", true);
        var other = new TradeRule("librarian", TradeDirection.SPEND, "minecraft:enchanted_book#minecraft:unbreaking=3", true);
        assertTrue(any.matches("librarian", offer));
        assertTrue(exact.matches("librarian", offer));
        assertFalse(other.matches("librarian", offer));
    }

    @Test
    void expectedDeltaForVerification() {
        var offer = OfferInfo.classify(0, List.of(item("emerald", 5), item("book", 1)),
                item("enchanted_book", 1), false, 0, 12);
        var delta = offer.expectedDelta();
        assertEquals(-5, delta.get("minecraft:emerald"));
        assertEquals(-1, delta.get("minecraft:book"));
        assertEquals(1, delta.get("minecraft:enchanted_book"));
        var earnOffer = earn(0, "wheat", 20, 1, false);
        assertEquals(-20, earnOffer.expectedDelta().get("minecraft:wheat"));
        assertEquals(1, earnOffer.expectedDelta().get("minecraft:emerald"));
    }

    @Test
    void unsupportedOffersAreIgnored() {
        assertNull(OfferInfo.classify(0, List.of(item("wheat", 3)), item("bread", 1), false, 0, 12));
        assertNull(OfferInfo.classify(0, List.of(item("emerald", 3)), item("emerald", 1), false, 0, 12));
    }

    @Test
    void professionPriorityOrderIsPreserved() {
        var rules = List.of(rule("farmer", TradeDirection.EARN, "carrot"), rule("librarian", TradeDirection.EARN, "paper"),
                rule("cleric", TradeDirection.EARN, "gold_ingot"), rule("fletcher", TradeDirection.EARN, "stick"));
        var order = List.of("cleric", "farmer", "fletcher", "librarian");
        assertEquals(order, TradePlan.activeProfessions(order, rules, id -> true));
    }

    @Test
    void planDropsUnknownDuplicateAndRulelessProfessions() {
        var rules = List.of(rule("farmer", TradeDirection.EARN, "carrot"), rule("cleric", TradeDirection.EARN, "gold_ingot"));
        var disabled = new TradeRule("butcher", TradeDirection.EARN, "minecraft:beef", false);
        var all = new java.util.ArrayList<>(rules);
        all.add(disabled);
        var order = List.of("farmer", "FARMER", "nitwit", "butcher", "cleric");
        var result = TradePlan.activeProfessions(order, all, id -> !id.equals("nitwit"));
        assertEquals(List.of("farmer", "cleric"), result);
    }
}
