package com.example.farmerautomation.trade;

import com.example.farmerautomation.AutomationConfig;
import com.example.farmerautomation.FarmerScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Seçilen mesleklerdeki köylüleri bulur. Yalnızca istemcinin yüklediği entity'lere bakar;
 * chunk yüklemeye veya sunucuya istek göndermeye çalışmaz.
 */
public final class VillagerScanner {
    private VillagerScanner() { }

    public static TradeProfession professionOf(Villager villager) {
        for (TradeProfession p : TradeProfession.values()) {
            if (villager.getVillagerData().profession().is(p.key())) return p;
        }
        return null; // nitwit, işsiz ve desteklenmeyen meslekler
    }

    public static List<Villager> scan(Minecraft client, Set<TradeProfession> professions) {
        List<Villager> result = new ArrayList<>();
        if (client == null || client.player == null || client.level == null || professions.isEmpty()) return result;

        double radius = Math.max(0, AutomationConfig.get().villagerSearchRadius);
        for (Entity entity : client.level.getEntities(client.player, client.player.getBoundingBox().inflate(radius))) {
            if (entity instanceof Villager villager && villager.isAlive()) {
                TradeProfession p = professionOf(villager);
                if (p != null && professions.contains(p)) result.add(villager);
            }
        }
        result.sort(Comparator.comparingDouble(v -> FarmerScanner.reachDistance(client, v)));
        return result;
    }
}
