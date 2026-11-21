package com.example.farmerautomation.render;

import com.example.farmerautomation.AutomationConfig;
import com.example.farmerautomation.AutomationManager;
import com.example.farmerautomation.AutomationState;
import com.example.farmerautomation.FarmerScanner;
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
import java.util.UUID;

/**
 * TradeAura-inspired farmer target boxes.
 * Eligible farmers are green; the farmer currently being traded with is yellow.
 * Rendering is visual-only and does not alter automation target selection.
 */
public final class VillagerOverlayRenderer {
    private static final int FARMER_STROKE = 0xFF28F05B;
    private static final int FARMER_FILL = 0x5528F05B;
    private static final int ACTIVE_STROKE = 0xFFFFD43B;
    private static final int ACTIVE_FILL = 0x66FFD43B;

    private VillagerOverlayRenderer() { }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(VillagerOverlayRenderer::render);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || client.gameRenderer == null) return;
        if (Boolean.FALSE.equals(AutomationConfig.get().villagerOverlayEnabled)) return;

        List<Villager> farmers = FarmerScanner.scanFarmers(client);
        if (farmers.isEmpty()) return;

        AutomationManager manager = AutomationManager.INSTANCE;
        UUID activeTarget = manager.isRunning() && manager.getState() == AutomationState.CYCLE
                ? manager.getActiveVillagerId()
                : null;

        Vec3 camera = client.gameRenderer.getMainCamera().position();
        boolean gizmoCollectorAvailable = true;

        for (Villager villager : farmers) {
            if (villager == null || !villager.isAlive()) continue;

            boolean active = activeTarget != null && activeTarget.equals(villager.getUUID());
            int stroke = active ? ACTIVE_STROKE : FARMER_STROKE;
            int fill = active ? ACTIVE_FILL : FARMER_FILL;

            if (gizmoCollectorAvailable) {
                try {
                    Gizmos.cuboid(villager.getBoundingBox(), GizmoStyle.strokeAndFill(
                            stroke, active ? 2.0f : 1.5f, fill));
                    continue;
                } catch (IllegalStateException unavailableOutsideCollector) {
                    // Some renderer replacements may invoke this event without a GizmoCollector.
                    // Fall back to the existing buffered outline instead of interrupting rendering.
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
