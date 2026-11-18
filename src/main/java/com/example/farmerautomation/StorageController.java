package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Moves only emerald stacks from the player inventory into explicitly selected nearby storage blocks. */
public final class StorageController {
    public enum Result { RUNNING, DONE, FAILED }
    private enum Step { IDLE, OPEN, WAIT_SCREEN, TRANSFER, WAIT_TRANSFER, CLOSE, DONE, FAILED }

    private final List<BlockPos> targets = new ArrayList<>();
    private Step step = Step.IDLE;
    private int targetIndex;
    private int ticks;
    private int emeraldsBefore;
    private String error;

    public void reset() {
        targets.clear();
        step = Step.IDLE;
        targetIndex = 0;
        ticks = 0;
        emeraldsBefore = 0;
        error = null;
    }

    public String getError() { return error; }

    public Result tick(Minecraft client) {
        AutomationConfig config = AutomationConfig.get();
        if (!config.storageEnabled) return Result.DONE;
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return fail("Depolama için oyuncu/dünya hazır değil.");
        }

        if (step == Step.IDLE) {
            if (InventoryManager.countEmeralds(client) <= 0) return Result.DONE;
            if (config.selectedStorageTypes == null || config.selectedStorageTypes.isEmpty()) {
                // Depolama isteğe bağlıdır. Tür seçilmemiş olması takas döngüsünü durdurmamalı.
                info(client, "Depolama türü seçilmedi; zümrütler envanterde bırakıldı.");
                step = Step.DONE;
                return Result.DONE;
            }
            scanTargets(client, config);
            if (targets.isEmpty()) {
                // No matching storage nearby is a normal optional-storage condition,
                // not a trade-cycle failure. Keep emeralds in the player's inventory.
                info(client, "Yakında seçili depolama bulunamadı; zümrütler envanterde bırakıldı.");
                step = Step.DONE;
                return Result.DONE;
            }
            step = Step.OPEN;
            info(client, targets.size() + " uygun depolama bulundu; zümrütler aktarılacak.");
        }

        switch (step) {
            case OPEN -> {
                if (InventoryManager.countEmeralds(client) <= 0) {
                    step = Step.DONE;
                    return Result.DONE;
                }
                if (targetIndex >= targets.size()) {
                    return fail("Depolama alanları dolu veya erişilemiyor. Aktarılamayan zümrütler envanterde bırakıldı.");
                }
                BlockPos pos = targets.get(targetIndex);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                ticks = 0;
                step = Step.WAIT_SCREEN;
            }
            case WAIT_SCREEN -> {
                if (client.screen instanceof AbstractContainerScreen<?> screen
                        && screen.getMenu().slots.size() > 36) {
                    ticks = 0;
                    step = Step.TRANSFER;
                    info(client, "Depolama açıldı: " + targets.get(targetIndex).toShortString());
                } else if (++ticks > 20) {
                    targetIndex++;
                    step = Step.OPEN;
                }
            }
            case TRANSFER -> {
                if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
                    return fail("Depolama ekranı aktarım sırasında kapandı. Kalan zümrütler envanterde bırakıldı.");
                }
                int emeralds = InventoryManager.countEmeralds(client);
                if (emeralds <= 0) {
                    client.player.closeContainer();
                    step = Step.DONE;
                    info(client, "Zümrütlerin tamamı depolamaya aktarıldı.");
                    return Result.DONE;
                }
                AbstractContainerMenu menu = screen.getMenu();
                int firstPlayerSlot = Math.max(0, menu.slots.size() - 36);
                int emeraldSlot = -1;
                for (int i = firstPlayerSlot; i < menu.slots.size(); i++) {
                    if (menu.getSlot(i).getItem().is(Items.EMERALD)) {
                        emeraldSlot = i;
                        break;
                    }
                }
                if (emeraldSlot < 0) {
                    client.player.closeContainer();
                    targetIndex++;
                    step = Step.OPEN;
                    return Result.RUNNING;
                }
                emeraldsBefore = emeralds;
                client.gameMode.handleInventoryMouseClick(menu.containerId, emeraldSlot, 0,
                        ClickType.QUICK_MOVE, client.player);
                ticks = 0;
                step = Step.WAIT_TRANSFER;
            }
            case WAIT_TRANSFER -> {
                int remaining = InventoryManager.countEmeralds(client);
                if (remaining < emeraldsBefore) {
                    step = Step.TRANSFER;
                } else if (++ticks > 10) {
                    if (client.screen instanceof AbstractContainerScreen<?>) client.player.closeContainer();
                    targetIndex++;
                    step = Step.OPEN;
                }
            }
            case DONE -> { return Result.DONE; }
            case FAILED -> { return Result.FAILED; }
            case IDLE -> { }
            case CLOSE -> {
                if (client.screen instanceof AbstractContainerScreen<?>) client.player.closeContainer();
                step = Step.OPEN;
            }
        }
        return step == Step.DONE ? Result.DONE : step == Step.FAILED ? Result.FAILED : Result.RUNNING;
    }

    private void scanTargets(Minecraft client, AutomationConfig config) {
        targets.clear();
        BlockPos origin = client.player.blockPosition();
        int radius = Math.max(1, Math.min(32, config.storageSearchRadius));
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    BlockPos pos = origin.offset(x, y, z);
                    if (!client.level.hasChunkAt(pos)) continue;
                    if (isSelected(client.level.getBlockState(pos), config)) targets.add(pos);
                }
            }
        }
        targets.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
    }

    private boolean isSelected(net.minecraft.world.level.block.state.BlockState state, AutomationConfig c) {
        if (c.isStorageTypeSelected("chest") && state.is(Blocks.CHEST)) return true;
        if (c.isStorageTypeSelected("trapped_chest") && state.is(Blocks.TRAPPED_CHEST)) return true;
        if (c.isStorageTypeSelected("barrel") && state.is(Blocks.BARREL)) return true;
        if (c.isStorageTypeSelected("ender_chest") && state.is(Blocks.ENDER_CHEST)) return true;
        if (c.isStorageTypeSelected("shulker_box") && state.is(Blocks.SHULKER_BOX)) return true;
        return (c.isStorageTypeSelected("white_shulker_box") && state.is(Blocks.WHITE_SHULKER_BOX))
                || (c.isStorageTypeSelected("orange_shulker_box") && state.is(Blocks.ORANGE_SHULKER_BOX))
                || (c.isStorageTypeSelected("magenta_shulker_box") && state.is(Blocks.MAGENTA_SHULKER_BOX))
                || (c.isStorageTypeSelected("light_blue_shulker_box") && state.is(Blocks.LIGHT_BLUE_SHULKER_BOX))
                || (c.isStorageTypeSelected("yellow_shulker_box") && state.is(Blocks.YELLOW_SHULKER_BOX))
                || (c.isStorageTypeSelected("lime_shulker_box") && state.is(Blocks.LIME_SHULKER_BOX))
                || (c.isStorageTypeSelected("pink_shulker_box") && state.is(Blocks.PINK_SHULKER_BOX))
                || (c.isStorageTypeSelected("gray_shulker_box") && state.is(Blocks.GRAY_SHULKER_BOX))
                || (c.isStorageTypeSelected("light_gray_shulker_box") && state.is(Blocks.LIGHT_GRAY_SHULKER_BOX))
                || (c.isStorageTypeSelected("cyan_shulker_box") && state.is(Blocks.CYAN_SHULKER_BOX))
                || (c.isStorageTypeSelected("purple_shulker_box") && state.is(Blocks.PURPLE_SHULKER_BOX))
                || (c.isStorageTypeSelected("blue_shulker_box") && state.is(Blocks.BLUE_SHULKER_BOX))
                || (c.isStorageTypeSelected("brown_shulker_box") && state.is(Blocks.BROWN_SHULKER_BOX))
                || (c.isStorageTypeSelected("green_shulker_box") && state.is(Blocks.GREEN_SHULKER_BOX))
                || (c.isStorageTypeSelected("red_shulker_box") && state.is(Blocks.RED_SHULKER_BOX))
                || (c.isStorageTypeSelected("black_shulker_box") && state.is(Blocks.BLACK_SHULKER_BOX));
    }

    private Result fail(String message) {
        error = message;
        step = Step.FAILED;
        return Result.FAILED;
    }

    private static void info(Minecraft client, String message) {
        ErrorHandler.info(message);
        // Sandık sayısı / açılma denemeleri sohbeti doldurmasın.
        // Yalnızca aktarımın tamamlandığı bilgisi oyun sohbetinde gösterilir.
        if (message.startsWith("Zümrütlerin tamamı depolamaya aktarıldı.")
                && client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6[Depolama]§r " + message), false);
        }
    }
}
