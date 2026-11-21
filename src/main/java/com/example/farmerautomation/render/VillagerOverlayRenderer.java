package com.example.farmerautomation.render;

import com.example.farmerautomation.AutomationConfig;
import com.example.farmerautomation.FarmerScanner;
import com.example.farmerautomation.AutomationManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.List;

/**
 * Green filled boxes for every eligible farmer.
 * The entity scan is cached per client tick so rendering does not rescan/sort every frame.
 */
public final class VillagerOverlayRenderer {
    private static final int UNTRADED_STROKE = 0xFF28F05B;
    private static final int UNTRADED_FILL = 0x5528F05B;
    private static final int TRADED_STROKE = 0xFFFF4545;
    private static final int TRADED_FILL = 0x55FF4545;

    private static List<Villager> cachedFarmers = List.of();
    private static net.minecraft.client.multiplayer.ClientLevel cachedLevel;
    private static long lastScanGameTime = Long.MIN_VALUE;

    private VillagerOverlayRenderer() { }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(VillagerOverlayRenderer::render);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || client.gameRenderer == null) return;
        if (Boolean.FALSE.equals(AutomationConfig.get().villagerOverlayEnabled)) return;

        long gameTime = client.level.getGameTime();
        if (cachedLevel != client.level || lastScanGameTime != gameTime) {
            cachedLevel = client.level;
            lastScanGameTime = gameTime;
            cachedFarmers = FarmerScanner.scanFarmers(client);
        }

        if (cachedFarmers.isEmpty()) return;

        Vec3 camera = client.gameRenderer.getMainCamera().position();
        boolean gizmoCollectorAvailable = true;

        for (Villager villager : cachedFarmers) {
            if (villager == null || !villager.isAlive()) continue;

            boolean traded = AutomationManager.INSTANCE.hasTradedWithVillager(villager.getUUID());
            int stroke = traded ? TRADED_STROKE : UNTRADED_STROKE;
            int fill = traded ? TRADED_FILL : UNTRADED_FILL;

            if (gizmoCollectorAvailable) {
                try {
                    Gizmos.cuboid(villager.getBoundingBox(),
                            GizmoStyle.strokeAndFill(stroke, 1.5f, fill));
                    continue;
                } catch (IllegalStateException unavailableOutsideCollector) {
                    // Fall back to the outline if a renderer replacement has no GizmoCollector.
                    gizmoCollectorAvailable = false;
                }
            }

            renderOutline(context, villager, camera, stroke);
        }
    }

    private static void renderOutline(
            net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context,
            Villager villager, Vec3 camera, int color) {
        if (context.matrices() == null || context.consumers() == null) return;

        PoseStack matrices = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        AABB relativeBox = villager.getBoundingBox().move(-camera.x, -camera.y, -camera.z);

        matrices.pushPose();
        try {
            ShapeRenderer.renderShape(matrices, lines, Shapes.create(relativeBox),
                    0.0, 0.0, 0.0, color, 1.5f);
        } finally {
            matrices.popPose();
        }
    }
}
