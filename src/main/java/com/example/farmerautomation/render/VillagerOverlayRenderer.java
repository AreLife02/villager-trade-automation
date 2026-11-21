package com.example.farmerautomation.render;

import com.example.farmerautomation.AutomationConfig;
import com.example.farmerautomation.trade.TradeProfession;
import com.example.farmerautomation.trade.VillagerScanner;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
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
            VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());
            for (Villager villager : villagers) {
                if (villager == null || !villager.isAlive()) continue;

                boolean isSelected = selected.contains(villager.getUUID().toString());
                AABB relativeBox = villager.getBoundingBox().move(-camera.x, -camera.y, -camera.z);

                int color = isSelected
                        ? packColor(242, 51, 255, 71)
                        : packColor(107, 148, 158, 173);

                // 1.21.11 moved line-box rendering to ShapeRenderer and RenderTypes.
                ShapeRenderer.renderShape(matrices, lines, Shapes.create(relativeBox),
                        0.0, 0.0, 0.0, color, 1.5f);
            }
        } finally {
            matrices.popPose();
        }
    }

    private static int packColor(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24
                | (red & 0xFF) << 16
                | (green & 0xFF) << 8
                | (blue & 0xFF);
    }
}
