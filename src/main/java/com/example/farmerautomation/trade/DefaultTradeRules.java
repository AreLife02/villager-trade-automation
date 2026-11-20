package com.example.farmerautomation.trade;

import java.util.ArrayList;
import java.util.List;

/**
 * Ayar dosyasının ilk oluşturulduğu varsayılan kural kataloğu. Yalnızca çiftçinin havuç → zümrüt
 * kuralı etkindir (mevcut davranış); diğerleri kapalıdır ve ayar dosyasında açılabilir.
 * Katalogdaki bir eşya köylüde gerçekte yoksa kural hiçbir zaman eşleşmez; teklif uydurulmaz.
 */
public final class DefaultTradeRules {
    private DefaultTradeRules() { }

    public static List<TradeRule> create() {
        List<TradeRule> rules = new ArrayList<>();
        rules.add(new TradeRule("farmer", TradeDirection.EARN, "minecraft:carrot", true));
        earn(rules, "farmer", "wheat", "potato", "beetroot", "pumpkin", "melon");
        spend(rules, "farmer", "bread", "pumpkin_pie", "apple", "cookie", "cake", "golden_carrot", "glistering_melon_slice");

        earn(rules, "librarian", "paper", "book", "ink_sac", "writable_book");
        spend(rules, "librarian", "bookshelf", "glass", "lantern", "clock", "compass", "name_tag", "enchanted_book");

        earn(rules, "armorer", "coal", "iron_ingot", "lava_bucket", "diamond");
        spend(rules, "armorer", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "bell", "shield");

        earn(rules, "butcher", "chicken", "porkchop", "rabbit", "mutton", "beef", "coal", "dried_kelp_block", "sweet_berries");
        spend(rules, "butcher", "rabbit_stew", "cooked_porkchop", "cooked_chicken");

        earn(rules, "cartographer", "paper", "glass_pane", "compass");
        spend(rules, "cartographer", "map", "item_frame", "glass_pane");

        earn(rules, "cleric", "rotten_flesh", "gold_ingot", "rabbit_foot", "turtle_scute", "glass_bottle", "nether_wart");
        spend(rules, "cleric", "redstone", "lapis_lazuli", "glowstone", "ender_pearl", "experience_bottle");

        earn(rules, "fisherman", "string", "coal", "cod", "salmon");
        spend(rules, "fisherman", "fishing_rod", "campfire", "cooked_cod", "cooked_salmon");

        earn(rules, "fletcher", "stick", "flint", "string", "feather", "tripwire_hook");
        spend(rules, "fletcher", "arrow", "bow", "crossbow");

        earn(rules, "leatherworker", "leather", "flint", "rabbit_hide", "turtle_scute");
        spend(rules, "leatherworker", "leather_leggings", "leather_chestplate", "leather_helmet", "leather_boots", "saddle");

        earn(rules, "mason", "clay_ball", "stone", "granite", "andesite", "diorite", "quartz");
        spend(rules, "mason", "brick", "polished_andesite", "polished_diorite", "polished_granite", "quartz_pillar");

        earn(rules, "shepherd", "white_wool", "black_wool", "gray_wool", "brown_wool");
        spend(rules, "shepherd", "shears", "white_wool", "white_carpet", "white_bed");

        earn(rules, "toolsmith", "coal", "iron_ingot", "flint", "diamond");
        spend(rules, "toolsmith", "stone_axe", "stone_shovel", "stone_pickaxe", "stone_hoe", "bell");

        earn(rules, "weaponsmith", "coal", "iron_ingot", "flint", "diamond");
        spend(rules, "weaponsmith", "iron_axe", "iron_sword", "bell");
        return rules;
    }

    private static void earn(List<TradeRule> rules, String profession, String... items) {
        for (String item : items) rules.add(new TradeRule(profession, TradeDirection.EARN, "minecraft:" + item, false));
    }

    private static void spend(List<TradeRule> rules, String profession, String... items) {
        for (String item : items) rules.add(new TradeRule(profession, TradeDirection.SPEND, "minecraft:" + item, false));
    }
}
