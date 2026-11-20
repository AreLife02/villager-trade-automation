package com.example.farmerautomation.trade;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.Locale;

/** Takas desteklenen 13 köylü mesleği. Nitwit ve işsiz köylüler bilerek yok. */
public enum TradeProfession {
    FARMER("Çiftçi", VillagerProfession.FARMER),
    LIBRARIAN("Kütüphaneci", VillagerProfession.LIBRARIAN),
    ARMORER("Zırhçı", VillagerProfession.ARMORER),
    BUTCHER("Kasap", VillagerProfession.BUTCHER),
    CARTOGRAPHER("Haritacı", VillagerProfession.CARTOGRAPHER),
    CLERIC("Rahip", VillagerProfession.CLERIC),
    FISHERMAN("Balıkçı", VillagerProfession.FISHERMAN),
    FLETCHER("Okçu", VillagerProfession.FLETCHER),
    LEATHERWORKER("Derici", VillagerProfession.LEATHERWORKER),
    MASON("Taşçı", VillagerProfession.MASON),
    SHEPHERD("Çoban", VillagerProfession.SHEPHERD),
    TOOLSMITH("Alet Ustası", VillagerProfession.TOOLSMITH),
    WEAPONSMITH("Silah Ustası", VillagerProfession.WEAPONSMITH);

    private final String displayName;
    private final ResourceKey<VillagerProfession> key;

    TradeProfession(String displayName, ResourceKey<VillagerProfession> key) {
        this.displayName = displayName;
        this.key = key;
    }

    public String displayName() { return displayName; }
    public ResourceKey<VillagerProfession> key() { return key; }

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
