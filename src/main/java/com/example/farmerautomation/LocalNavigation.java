package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bağımsız istemci tarafı yol bulma ve hareket sistemi.
 * Blok kırmaz/yerleştirmez; yalnızca mevcut, yüklü ve güvenli zemin üzerinde yürür.
 */
public final class LocalNavigation {
    public enum Status { RUNNING, REACHED, FAILED }

    private static final int MAX_EXPANSIONS = 12_000;
    private static final int MAX_REPLANS = 2;
    private static final int STUCK_TICKS = 40;

    private final List<BlockPos> path = new ArrayList<>();
    private int pathIndex;
    private int stuckTicks;
    private int replanCount;
    private Vec3 lastPosition;
    private BlockPos plannedTarget;
    private String lastError;

    public boolean start(Minecraft client, Villager target) {
        stop();
        lastError = null;
        replanCount = 0;

        if (!valid(client, target)) {
            lastError = "Oyuncu, dünya veya hedef köylü hazır değil.";
            return false;
        }
        if (FarmerScanner.isAccessible(client, target)) {
            faceEntity(client, target);
            return true;
        }
        return plan(client, target);
    }

    public Status tick(Minecraft client, Villager target) {
        if (!valid(client, target)) return fail("Hedef çiftçi artık mevcut değil.");

        if (FarmerScanner.isAccessible(client, target)) {
            releaseKeys(client);
            faceEntity(client, target);
            return Status.REACHED;
        }

        if (plannedTarget == null || plannedTarget.distSqr(target.blockPosition()) > 1.0) {
            if (!replan(client, target)) return Status.FAILED;
        }

        if (pathIndex >= path.size()) {
            if (!replan(client, target)) return Status.FAILED;
            if (pathIndex >= path.size()) return fail("Hedefe ulaşan güvenli bir rota bulunamadı.");
        }

        Vec3 currentPosition = client.player.position();
        if (lastPosition != null && currentPosition.distanceToSqr(lastPosition) < 0.0004) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPosition = currentPosition;

        if (stuckTicks > STUCK_TICKS) {
            if (!replan(client, target)) return Status.FAILED;
            stuckTicks = 0;
        }

        while (pathIndex < path.size()) {
            BlockPos waypoint = path.get(pathIndex);
            double dx = waypoint.getX() + 0.5 - client.player.getX();
            double dz = waypoint.getZ() + 0.5 - client.player.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < 0.28 && Math.abs(client.player.getY() - waypoint.getY()) < 0.8) {
                pathIndex++;
            } else {
                break;
            }
        }

        if (pathIndex >= path.size()) {
            if (FarmerScanner.isAccessible(client, target)) {
                releaseKeys(client);
                faceEntity(client, target);
                return Status.REACHED;
            }
            if (!replan(client, target)) return Status.FAILED;
            if (pathIndex >= path.size()) return fail("Rota tamamlandı ancak köylüye etkileşim mesafesine ulaşılamadı.");
        }

        BlockPos waypoint = path.get(pathIndex);
        double dx = waypoint.getX() + 0.5 - client.player.getX();
        double dz = waypoint.getZ() + 0.5 - client.player.getZ();
        float desiredYaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));

        client.player.setYRot(desiredYaw);
        client.player.setYHeadRot(desiredYaw);
        client.player.setXRot(0.0F);

        client.options.keyUp.setDown(true);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyJump.setDown(
                waypoint.getY() > Mth.floor(client.player.getY()) && client.player.onGround());

        return Status.RUNNING;
    }

    public void stop() {
        releaseKeys(Minecraft.getInstance());
        path.clear();
        pathIndex = 0;
        stuckTicks = 0;
        lastPosition = null;
        plannedTarget = null;
    }

    public String getLastError() {
        return lastError;
    }

    private Status fail(String message) {
        lastError = message;
        releaseKeys(Minecraft.getInstance());
        return Status.FAILED;
    }

    /** Oyuncu kamerasını doğrudan hedef köylünün yüzüne çevirir. */
    public static void faceEntity(Minecraft client, Villager villager) {
        if (client == null || client.player == null || villager == null) return;

        Vec3 from = client.player.getEyePosition();
        Vec3 to = villager.getEyePosition();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        float pitch = (float) (-Math.atan2(dy, horizontal) * (180.0 / Math.PI));
        client.player.setYRot(yaw);
        client.player.setYHeadRot(yaw);
        client.player.setXRot(pitch);
    }

    private boolean replan(Minecraft client, Villager target) {
        if (replanCount >= MAX_REPLANS) {
            lastError = "Hedefe giden güvenli rota bulunamadı veya oyuncu ilerleyemedi.";
            releaseKeys(client);
            return false;
        }
        replanCount++;
        return plan(client, target);
    }

    private boolean plan(Minecraft client, Villager target) {
        path.clear();
        pathIndex = 0;
        stuckTicks = 0;
        lastPosition = client.player.position();
        plannedTarget = target.blockPosition();

        BlockPos start = new BlockPos(
                Mth.floor(client.player.getX()),
                Mth.floor(client.player.getY()),
                Mth.floor(client.player.getZ()));

        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::f));
        Map<BlockPos, Double> scores = new HashMap<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();

        scores.put(start, 0.0);
        open.add(new SearchNode(start, 0.0, heuristic(start, target)));
        BlockPos goal = null;

        int maxHorizontal = Math.max(12, Math.min(64, AutomationConfig.get().villagerSearchRadius + 8));
        int minY = start.getY() - 8;
        int maxY = start.getY() + 8;
        int expanded = 0;

        while (!open.isEmpty() && expanded < MAX_EXPANSIONS) {
            SearchNode node = open.poll();
            Double bestKnown = scores.get(node.pos());
            if (bestKnown == null || node.g() > bestKnown + 0.0001) continue;

            if (isGoal(client, node.pos(), target)) {
                goal = node.pos();
                break;
            }
            expanded++;

            for (int[] direction : DIRECTIONS) {
                for (int dy : HEIGHT_CHANGES) {
                    BlockPos next = node.pos().offset(direction[0], dy, direction[1]);
                    if (next.getY() < minY || next.getY() > maxY) continue;
                    if (Math.abs(next.getX() - start.getX()) > maxHorizontal
                            || Math.abs(next.getZ() - start.getZ()) > maxHorizontal) continue;
                    if (!client.level.hasChunkAt(next) || !isSafeStand(client, next)) continue;

                    double stepCost = 1.0 + (dy > 0 ? 0.35 : dy < 0 ? 0.15 : 0.0);
                    double nextG = node.g() + stepCost;
                    Double old = scores.get(next);
                    if (old != null && nextG >= old) continue;

                    scores.put(next, nextG);
                    parents.put(next, node.pos());
                    open.add(new SearchNode(next, nextG, nextG + heuristic(next, target)));
                }
            }
        }

        if (goal == null) {
            lastError = expanded >= MAX_EXPANSIONS
                    ? "Yol arama sınırına ulaşıldı; daha kısa veya açık bir rota gerekli."
                    : "Köylüye ulaşan güvenli bir yol bulunamadı.";
            releaseKeys(client);
            return false;
        }

        BlockPos cursor = goal;
        while (cursor != null) {
            path.add(cursor);
            cursor = parents.get(cursor);
        }
        java.util.Collections.reverse(path);
        pathIndex = path.size() > 1 ? 1 : 0;

        if (path.size() == 1 && !FarmerScanner.isAccessible(client, target)) {
            lastError = "Başlangıç noktası hedefe erişim sağlamıyor.";
            return false;
        }
        return true;
    }

    private static boolean isGoal(Minecraft client, BlockPos feet, Villager target) {
        Vec3 eye = new Vec3(
                feet.getX() + 0.5,
                feet.getY() + client.player.getEyeHeight(),
                feet.getZ() + 0.5);
        // Yolun bitişini AABB'nin kenarına göre değil, sunucunun
        // etkileşim mesafesi denetimine daha yakın olan entity merkezine göre seç.
        double safeRange = Math.max(1.0, Math.min(2.5, client.player.entityInteractionRange() - 0.25));
        // Bu kontrol, oyuncunun şu anki konumunu değil, A*'ın değerlendirdiği
        // olası varış noktasını kullanmalı. Aksi halde uzaktaki oyuncu için
        // hiçbir düğüm hedef olamaz ve arama daima başarısız olur.
        Vec3 candidatePosition = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        if (candidatePosition.distanceToSqr(target.position()) > safeRange * safeRange) return false;

        Vec3[] points = {target.getEyePosition(),
                target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)};
        for (Vec3 point : points) {
            HitResult hit = client.level.clip(new ClipContext(
                    eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static double distanceToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean isSafeStand(Minecraft client, BlockPos feet) {
        var level = client.level;
        var below = feet.below();
        if (!level.hasChunkAt(below)) return false;

        var floorState = level.getBlockState(below);
        if (floorState.getCollisionShape(level, below).isEmpty()) return false;
        if (isHazard(floorState.getBlock())) return false;

        var feetState = level.getBlockState(feet);
        if (!feetState.getFluidState().isEmpty() || isHazard(feetState.getBlock())) return false;

        double width = client.player.getBbWidth();
        double height = client.player.getBbHeight();
        AABB playerBox = new AABB(
                feet.getX() + 0.5 - width / 2.0, feet.getY(), feet.getZ() + 0.5 - width / 2.0,
                feet.getX() + 0.5 + width / 2.0, feet.getY() + height, feet.getZ() + 0.5 + width / 2.0);
        return level.noCollision(client.player, playerBox);
    }

    private static boolean isHazard(net.minecraft.world.level.block.Block block) {
        return block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.SOUL_FIRE
                || block == Blocks.MAGMA_BLOCK || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.CACTUS || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POWDER_SNOW
                || block == Blocks.WITHER_ROSE || block == Blocks.POINTED_DRIPSTONE;
    }

    private static double heuristic(BlockPos from, Villager target) {
        BlockPos to = target.blockPosition();
        return Math.abs(from.getX() - to.getX())
                + Math.abs(from.getZ() - to.getZ())
                + Math.abs(from.getY() - to.getY()) * 1.4;
    }

    private static boolean valid(Minecraft client, Villager target) {
        return client != null && client.player != null && client.level != null
                && client.gameMode != null && target != null && target.isAlive();
    }

    private static void releaseKeys(Minecraft client) {
        if (client == null || client.options == null) return;
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keySprint.setDown(false);
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[] HEIGHT_CHANGES = {0, 1, -1};

    private record SearchNode(BlockPos pos, double g, double f) { }
}
