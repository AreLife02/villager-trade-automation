package com.example.farmerautomation.render;

import com.example.farmerautomation.AutomationConfig;
import com.example.farmerautomation.trade.TradeProfession;
import com.example.farmerautomation.trade.VillagerScanner;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight TradeAura-inspired villager outlines.
 * Selected villagers are green; detected but unselected villagers are muted gray.
 * This is a visual aid only and never changes automation targets or behavior.
 */
public final class VillagerOverlayRenderer {
    private VillagerOverlayRenderer() { }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> render(context.matrices(), context.consumers()));
    }

    private static void render(PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource consumers) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || client.gameRenderer == null) return;

        AutomationConfig config = AutomationConfig.get();
        if (Boolean.FALSE.equals(config.villagerOverlayEnabled)) return;

        List<Villager> villagers = VillagerScanner.scan(client, EnumSet.allOf(TradeProfession.class));
        if (villagers.isEmpty()) return;

        Set<String> selected = config.selectedVillagerIds == null
                ? Set.of()
                : new HashSet<>(config.selectedVillagerIds);

        Vec3 camera = client.gameRenderer.getMainCamera().position();
        matrices.pushPose();
        try {
            VertexConsumer lines = consumers.getBuffer(RenderType.lines());
            for (Villager villager : villagers) {
                if (villager == null || !villager.isAlive()) continue;

                boolean isSelected = selected.contains(villager.getUUID().toString());
                AABB relativeBox = villager.getBoundingBox().move(-camera.x, -camera.y, -camera.z);

                float red = isSelected ? 0.20f : 0.58f;
                float green = isSelected ? 1.00f : 0.62f;
                float blue = isSelected ? 0.28f : 0.68f;
                float alpha = isSelected ? 0.95f : 0.42f;

                LevelRenderer.renderLineBox(matrices, lines, relativeBox, red, green, blue, alpha);
            }
        } finally {
            matrices.popPose();
        }
    }
}
