package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bulunulan noktadan gerçekten erişilebilen çiftçileri (menzil + görüş) en yakından uzağa bulur. */
public final class FarmerScanner {
    private FarmerScanner() {
    }

    public static List<Villager> scanAccessible(Minecraft client) {
        List<Villager> result = new ArrayList<>();
        double radius = Math.max(0, AutomationConfig.get().villagerSearchRadius);

        for (Entity entity : client.level.getEntities(client.player, client.player.getBoundingBox().inflate(radius))) {
            if (entity instanceof Villager villager
                    && villager.isAlive()
                    && villager.getVillagerData().profession().is(VillagerProfession.FARMER)
                    && isAccessible(client, villager)) {
                result.add(villager);
            }
        }

        result.sort(Comparator.comparingDouble(v -> reachDistance(client, v)));
        return result;
    }

    public static boolean isAccessible(Minecraft client, Villager villager) {
        return villager.isAlive()
                && reachDistance(client, villager) <= client.player.entityInteractionRange()
                && hasLineOfSight(client, villager);
    }

    public static double reachDistance(Minecraft client, Villager villager) {
        Vec3 eye = client.player.getEyePosition();
        AABB box = villager.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eye.x, 0.0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0.0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0.0), eye.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean hasLineOfSight(Minecraft client, Villager villager) {
        Vec3 eye = client.player.getEyePosition();
        Vec3[] targets = {villager.getEyePosition(), villager.position().add(0, villager.getBbHeight() / 2.0, 0)};
        for (Vec3 target : targets) {
            HitResult hit = client.level.clip(new ClipContext(
                    eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
}
