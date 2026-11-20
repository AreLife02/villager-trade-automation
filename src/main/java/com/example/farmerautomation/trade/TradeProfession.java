package com.example.farmerautomation.trade;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Takas desteklenen 13 köylü mesleği. Nitwit ve işsiz köylüler bilerek yok. */
public enum TradeProfession {
    FARMER("Çiftçi", VillagerProfession.FARMER, Items.WHEAT),
    LIBRARIAN("Kütüphaneci", VillagerProfession.LIBRARIAN, Items.BOOK),
    ARMORER("Zırhçı", VillagerProfession.ARMORER, Items.IRON_CHESTPLATE),
    BUTCHER("Kasap", VillagerProfession.BUTCHER, Items.COOKED_BEEF),
    CARTOGRAPHER("Haritacı", VillagerProfession.CARTOGRAPHER, Items.MAP),
    CLERIC("Rahip", VillagerProfession.CLERIC, Items.ENDER_PEARL),
    FISHERMAN("Balıkçı", VillagerProfession.FISHERMAN, Items.FISHING_ROD),
    FLETCHER("Okçu", VillagerProfession.FLETCHER, Items.BOW),
    LEATHERWORKER("Derici", VillagerProfession.LEATHERWORKER, Items.LEATHER),
    MASON("Taşçı", VillagerProfession.MASON, Items.BRICK),
    SHEPHERD("Çoban", VillagerProfession.SHEPHERD, Items.WHITE_WOOL),
    TOOLSMITH("Alet Ustası", VillagerProfession.TOOLSMITH, Items.IRON_PICKAXE),
    WEAPONSMITH("Silah Ustası", VillagerProfession.WEAPONSMITH, Items.IRON_SWORD);

    private final String displayName;
    private final ResourceKey<VillagerProfession> key;
    private final Item icon;

    TradeProfession(String displayName, ResourceKey<VillagerProfession> key, Item icon) {
        this.displayName = displayName;
        this.key = key;
        this.icon = icon;
    }

    public String displayName() { return displayName; }
    public ResourceKey<VillagerProfession> key() { return key; }
    public Item icon() { return icon; }

    /** Ayar dosyasında saklanan kimlik (ör. "farmer"). */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static TradeProfession fromId(String id) {
        if (id == null) return null;
        for (TradeProfession p : values()) {
            if (p.id().equalsIgnoreCase(id)) return p;
        }
        return null;
    }
}
