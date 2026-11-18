package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Aşama A: yalnızca yakındaki çiftçileri tarar ve konum/erişilebilirlik bilgisi verir.
 * Bu aşamada hareket, Shop komutu, menü açma veya takas işlemi yapılmaz.
 */
public final class DiagnosticController {
    private boolean done;

    public void reset() {
        done = false;
    }

    public boolean tick(Minecraft client) {
        if (done) return true;
        if (client == null || client.player == null || client.level == null) return false;

        try {
            double radius = Math.max(0.0, AutomationConfig.get().villagerSearchRadius);
            double interactionRange = client.player.entityInteractionRange();
            List<Villager> farmers = new ArrayList<>();
            int villagerCount = 0;

            for (Entity entity : client.level.getEntities(
                    client.player, client.player.getBoundingBox().inflate(radius))) {
                if (!(entity instanceof Villager villager) || !villager.isAlive()) continue;
                villagerCount++;
                if (villager.getVillagerData().profession().is(VillagerProfession.FARMER)) {
                    farmers.add(villager);
                }
            }

            farmers.sort(Comparator.comparingDouble(v -> FarmerScanner.reachDistance(client, v)));
            AutomationManager.INSTANCE.setVillagerCount(farmers.size());

            chat(client, "=== AŞAMA A: ÇİFTÇİ TARAMASI ===");
            chat(client, "Oyuncu konumu: X=" + floor(client.player.getX())
                    + " Y=" + floor(client.player.getY()) + " Z=" + floor(client.player.getZ()));
            chat(client, "Tarama yarıçapı: " + String.format(Locale.ROOT, "%.1f", radius)
                    + " blok | Yakındaki köylü: " + villagerCount + " | Çiftçi: " + farmers.size());

            int index = 0;
            for (Villager villager : farmers) {
                index++;
                int dx = floor(villager.getX()) - floor(client.player.getX());
                int dy = floor(villager.getY()) - floor(client.player.getY());
                int dz = floor(villager.getZ()) - floor(client.player.getZ());
                double distance = FarmerScanner.reachDistance(client, villager);

                String floorLabel = dy > 1 ? "ÜST KAT" : dy < -1 ? "ALT KAT" : "AYNI SEVİYE";
                String access;
                if (distance > interactionRange) {
                    access = "MENZİL DIŞI";
                } else if (!FarmerScanner.isAccessible(client, villager)) {
                    access = "GÖRÜŞ ENGELLİ";
                } else {
                    access = "ERİŞİLEBİLİR";
                }

                chat(client, index + ". çiftçi: X=" + floor(villager.getX())
                        + " Y=" + floor(villager.getY()) + " Z=" + floor(villager.getZ())
                        + " | Fark: X" + signed(dx) + " Y" + signed(dy) + " Z" + signed(dz)
                        + " | " + floorLabel
                        + " | Mesafe: " + String.format(Locale.ROOT, "%.1f", distance)
                        + " blok | " + access);
            }

            if (farmers.isEmpty()) {
                chat(client, "Tarama alanında çiftçi köylü bulunamadı.");
            }

            chat(client, "Aşama A tamamlandı. Hareket, Shop ve takas yapılmadı.");
            done = true;
            return true;
        } catch (Exception exception) {
            done = true;
            AutomationManager.INSTANCE.fail(
                    "Çiftçi taraması hatası: " + exception.getClass().getSimpleName());
            chat(client, "Tarama hatası: " + exception.getClass().getSimpleName());
            return true;
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static void chat(Minecraft client, String message) {
        ErrorHandler.info(message);
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("§6[Otomasyon]§r " + message), false);
        }
    }
}
